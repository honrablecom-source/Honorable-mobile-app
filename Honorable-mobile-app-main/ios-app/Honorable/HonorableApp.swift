import SwiftUI

@main struct HonorableApp: App {
    var body: some Scene { WindowGroup { HomeView() } }
}

struct HomeView: View {
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("Private intelligence, on your device").foregroundStyle(.secondary)
                    FeatureCard(icon: "photo.stack", title: "Memories AI", detail: "Find a moment using ordinary words.")
                    FeatureCard(icon: "doc.text.magnifyingglass", title: "Terms AI", detail: "Understand the fine print before you agree.")
                    Label("Local processing • Nothing uploaded", systemImage: "lock.shield").font(.footnote).padding(.top, 18)
                }.padding(24)
            }.navigationTitle("Honorable")
        }.tint(.indigo)
    }
}

private struct FeatureCard: View {
    let icon: String, title: String, detail: String
    var body: some View {
        HStack(spacing: 18) {
            Image(systemName: icon).font(.title2).frame(width: 52, height: 52).background(.indigo.gradient).foregroundStyle(.white).clipShape(Circle())
            VStack(alignment: .leading, spacing: 5) { Text(title).font(.title2.bold()); Text(detail).foregroundStyle(.secondary) }
            Spacer(); Image(systemName: "chevron.right").foregroundStyle(.tertiary)
        }.padding(20).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 28))
    }
}
