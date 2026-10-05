import Shared
import SwiftUI
import UIKit

@main
struct IlmeryaApp: App {
    @StateObject private var host = GameHost()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            GameView(host: host)
                .ignoresSafeArea()
                .onChange(of: scenePhase) { phase in
                    if phase == .active {
                        host.ads.activate()
                    } else {
                        host.session.pause()
                    }
                }
        }
    }
}

private final class GameHost: ObservableObject {
    let ads: IOSAdsManager
    let gameCenter = GameCenterAuth()
    let session: IosGameSession

    init() {
        let ads = IOSAdsManager()
        self.ads = ads
        // An empty URL keeps the league switched off and says so on the league screen.
        let api = (Bundle.main.object(forInfoDictionaryKey: "IlmeryaLeagueURL") as? String) ?? ""
        let leagueEnabled = api.hasPrefix("https://")
        var scene: String? = nil
        #if DEBUG
        // Store screenshots: xcrun simctl launch <udid> <bundle> -ilmeryaScene board
        let arguments = ProcessInfo.processInfo.arguments
        if let index = arguments.firstIndex(of: "-ilmeryaScene"), index + 1 < arguments.count { scene = arguments[index + 1] }
        #endif
        session = IosGameSession(adsManager: ads, gameCenter: leagueEnabled ? gameCenter : nil, apiUrl: api, demoScene: scene)
        if leagueEnabled { gameCenter.installHandler() }
    }

    deinit { session.dispose() }
}

private struct GameView: UIViewControllerRepresentable {
    let host: GameHost

    func makeUIViewController(context: Context) -> GameContainerController {
        GameContainerController(host: host)
    }

    func updateUIViewController(_ uiViewController: GameContainerController, context: Context) {}
}

private final class GameContainerController: UIViewController {
    let host: GameHost

    init(host: GameHost) {
        self.host = host
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("Use init(host:)") }
    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }

    override func viewDidLoad() {
        super.viewDidLoad()
        let game = host.session.makeViewController()
        addChild(game)
        game.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(game.view)
        NSLayoutConstraint.activate([
            game.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            game.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            game.view.topAnchor.constraint(equalTo: view.topAnchor),
            game.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        game.didMove(toParent: self)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        host.ads.bind(presenter: self)
        host.gameCenter.bind(presenter: self)
        host.ads.activate()
    }
}
