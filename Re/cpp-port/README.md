# Reverend Insanity native C++ port

This is a dependency-light native port of the playable Qing Mao exploration slice. The original single-file HTML game remains in `Re/ri game main 5.html` as the complete reference build while systems are migrated incrementally.

## Build and run

From this directory:

```bash
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j2
./run.sh
```

In Codespaces or another terminal without a graphical display, `./run.sh`
automatically starts the full browser-playable build on port 8080. Open the
forwarded port from the Codespaces **Ports** tab. You can explicitly select it
or change its port with:

```bash
./run.sh --web
./run.sh --web --port 8765
```

Controls: WASD/arrows move, E interacts or attacks, Q flees combat, and Escape closes a panel or exits. Progress is saved automatically to `ri_cpp_save.txt` inside this directory.

For a headless test:

```bash
./build/ri_native --self-test
```

The port uses X11 directly because this environment has no SDL, SFML, Raylib, or Emscripten installation. It retains the optimized world-coordinate simulation, camera, collisions, NPCs, resources, enemies, discovery, world time, minimap, and save/load foundation. The HTML version remains the authoritative build for the full Gu, cultivation, auction, and arena interfaces until those large systems are migrated natively.
