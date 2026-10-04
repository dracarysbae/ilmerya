import GameKit
import Shared
import UIKit

/// GameKit identity for the weekly league. Only a signed identity-verification proof leaves the
/// device; the league server checks Apple's signature, bundle ID and timestamp before creating a session.
final class GameCenterAuth: NSObject, GameCenterBridge {
    private weak var presenter: UIViewController?
    private var loginController: UIViewController?
    private var handlerInstalled = false
    private var waiting: [(Bool, (String?) -> Void)] = []

    func bind(presenter: UIViewController) { self.presenter = presenter }

    /// Apple recommends installing the handler once at launch; it signs in silently when possible.
    func installHandler() {
        guard !handlerInstalled else { return }
        handlerInstalled = true
        GKLocalPlayer.local.authenticateHandler = { [weak self] controller, _ in
            guard let self else { return }
            self.loginController = controller
            self.flush()
        }
    }

    func authenticate(interactive: Bool, completion: @escaping (String?) -> Void) {
        DispatchQueue.main.async {
            if GKLocalPlayer.local.isAuthenticated {
                self.proof(completion)
                return
            }
            if interactive, let login = self.loginController, let presenter = self.presenter, presenter.presentedViewController == nil {
                self.waiting.append((true, completion))
                presenter.present(login, animated: true)
                return
            }
            if !self.handlerInstalled {
                self.waiting.append((interactive, completion))
                self.installHandler()
                return
            }
            if interactive && self.loginController == nil {
                // Game Center is disabled or the player cancelled earlier; Apple shows no further prompt.
                self.openSettingsHint()
            }
            completion(nil)
        }
    }

    private func flush() {
        let pending = waiting
        waiting.removeAll()
        for (interactive, completion) in pending {
            if GKLocalPlayer.local.isAuthenticated {
                proof(completion)
            } else if interactive, let login = loginController, let presenter, presenter.presentedViewController == nil {
                waiting.append((true, completion))
                presenter.present(login, animated: true)
            } else {
                completion(nil)
            }
        }
    }

    private func proof(_ completion: @escaping (String?) -> Void) {
        let player = GKLocalPlayer.local
        player.fetchItems(forIdentityVerificationSignature: { url, signature, salt, timestamp, error in
            DispatchQueue.main.async {
                guard error == nil, let url, let signature, let salt, let bundle = Bundle.main.bundleIdentifier else {
                    completion(nil)
                    return
                }
                let body: [String: Any] = [
                    "teamPlayerID": player.teamPlayerID,
                    "bundleID": bundle,
                    // A decimal string keeps the full unsigned 64-bit millisecond timestamp.
                    "timestamp": String(timestamp),
                    "salt": salt.base64EncodedString(),
                    "signature": signature.base64EncodedString(),
                    "publicKeyURL": url.absoluteString,
                    "displayName": player.displayName
                ]
                guard let data = try? JSONSerialization.data(withJSONObject: body),
                      let text = String(data: data, encoding: .utf8) else {
                    completion(nil)
                    return
                }
                completion(text)
            }
        })
    }

    private func openSettingsHint() {
        guard let presenter, presenter.presentedViewController == nil else { return }
        let turkish = Locale.preferredLanguages.first?.hasPrefix("tr") ?? false
        let alert = UIAlertController(
            title: turkish ? "Game Center kapalı" : "Game Center is off",
            message: turkish ? "Lige katılmak için Ayarlar > Game Center bölümünden oturum aç." : "To join the league, sign in under Settings > Game Center.",
            preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: turkish ? "Tamam" : "OK", style: .default))
        presenter.present(alert, animated: true)
    }
}
