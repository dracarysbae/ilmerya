import GoogleMobileAds
import Shared
import UIKit
import UserMessagingPlatform

/// Main-thread adapter for the shared post-game gate. It never presents on load completion.
final class IOSAdsManager: NSObject, AdsManager, FullScreenContentDelegate {
    private weak var presenter: UIViewController?
    private var interstitial: InterstitialAd?
    private var presentedAd: InterstitialAd?
    private var loadedAt: Date?
    private var loading = false
    private var started = false
    private var initialized = false
    private var consentRequested = false
    private var consentInProgress = false
    private var generation = 0
    private var retryAt = Date.distantPast
    private var completion: (() -> KotlinUnit)?
    private var retryWork: DispatchWorkItem?

    private var unitID: String {
        #if DEBUG
        return "ca-app-pub-3940256099942544/4411468910"
        #else
        return Bundle.main.object(forInfoDictionaryKey: "IlmeryaInterstitialAdUnitID") as? String ?? ""
        #endif
    }

    var isPrivacyOptionsRequired: Bool {
        ConsentInformation.shared.privacyOptionsRequirementStatus == .required
    }

    var isAdReady: Bool {
        guard ConsentInformation.shared.canRequestAds, !consentInProgress,
              completion == nil, interstitial != nil, let loadedAt else { return false }
        return Date().timeIntervalSince(loadedAt) < 3_600
    }

    func bind(presenter: UIViewController) { self.presenter = presenter }

    func activate() {
        guard safePresenter != nil else { return }
        if !consentRequested { gatherConsent() } else { loadAd() }
    }

    private var safePresenter: UIViewController? {
        guard UIApplication.shared.applicationState == .active,
              let presenter, presenter.viewIfLoaded?.window != nil,
              presenter.presentedViewController == nil,
              !presenter.isBeingDismissed else { return nil }
        return presenter
    }

    private func gatherConsent() {
        guard let presenter = safePresenter else { return }
        consentRequested = true
        consentInProgress = true
        ConsentInformation.shared.requestConsentInfoUpdate(with: RequestParameters()) { [weak self, weak presenter] error in
            Task { @MainActor in
                guard let self else { return }
                if error == nil, let presenter, presenter.viewIfLoaded?.window != nil,
                   presenter.presentedViewController == nil,
                   UIApplication.shared.applicationState == .active {
                    do { try await ConsentForm.loadAndPresentIfRequired(from: presenter) }
                    catch { NSLog("Ilmerya consent form: %@", error.localizedDescription) }
                } else if let error {
                    NSLog("Ilmerya consent update: %@", error.localizedDescription)
                    if !ConsentInformation.shared.canRequestAds { self.consentRequested = false }
                } else {
                    // Foreground/presenter was lost during the request; try the form on return.
                    self.consentRequested = false
                }
                self.consentInProgress = false
                // A failed update may still leave valid consent from a previous launch.
                self.startIfAllowed()
            }
        }
    }

    private func startIfAllowed() {
        guard ConsentInformation.shared.canRequestAds, !consentInProgress else { return }
        if started { if initialized { loadAd() }; return }
        started = true
        MobileAds.shared.start { [weak self] _ in
            DispatchQueue.main.async {
                guard let self else { return }
                self.initialized = true
                self.loadAd()
            }
        }
    }

    func loadAd() {
        guard ConsentInformation.shared.canRequestAds, !consentInProgress else { return }
        guard initialized else { startIfAllowed(); return }
        guard !loading, completion == nil, !isAdReady, !unitID.isEmpty,
              UIApplication.shared.applicationState == .active, Date() >= retryAt else { return }
        interstitial = nil
        loadedAt = nil
        loading = true
        let requestGeneration = generation
        Task { @MainActor [weak self] in
            guard let self else { return }
            do {
                let ad = try await InterstitialAd.load(with: self.unitID, request: Request())
                guard self.generation == requestGeneration else { return }
                self.loading = false
                guard ConsentInformation.shared.canRequestAds, !self.consentInProgress else { return }
                self.interstitial = ad
                self.loadedAt = Date()
                ad.fullScreenContentDelegate = self
            } catch {
                guard self.generation == requestGeneration else { return }
                self.loading = false
                self.retryAt = Date().addingTimeInterval(60)
                NSLog("Ilmerya interstitial load: %@", error.localizedDescription)
                self.scheduleRetry()
            }
        }
    }

    private func scheduleRetry() {
        retryWork?.cancel()
        let work = DispatchWorkItem { [weak self] in self?.loadAd() }
        retryWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 61, execute: work)
    }

    func showInterstitial(onFinished: @escaping () -> KotlinUnit) {
        guard completion == nil, isAdReady, let presenter = safePresenter, let ad = interstitial else {
            _ = onFinished()
            loadAd()
            return
        }
        do { try ad.canPresent(from: presenter) }
        catch {
            interstitial = nil
            loadedAt = nil
            _ = onFinished()
            loadAd()
            return
        }
        completion = onFinished
        presentedAd = ad
        interstitial = nil
        loadedAt = nil
        ad.present(from: presenter)
    }

    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) { finishPresentation() }

    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        NSLog("Ilmerya interstitial presentation: %@", error.localizedDescription)
        finishPresentation()
    }

    private func finishPresentation() {
        presentedAd = nil
        let finished = completion
        completion = nil
        _ = finished?()
        loadAd()
    }

    func showPrivacyOptions(onFinished: @escaping () -> KotlinUnit) {
        guard !consentInProgress, completion == nil, isPrivacyOptionsRequired,
              let presenter = safePresenter else { _ = onFinished(); return }
        consentInProgress = true
        generation += 1
        loading = false
        interstitial = nil
        loadedAt = nil
        retryWork?.cancel()
        Task { @MainActor [weak self] in
            guard let self else { _ = onFinished(); return }
            do { try await ConsentForm.presentPrivacyOptionsForm(from: presenter) }
            catch { NSLog("Ilmerya privacy options: %@", error.localizedDescription) }
            self.consentInProgress = false
            _ = onFinished()
            self.startIfAllowed()
        }
    }

    deinit { retryWork?.cancel() }
}
