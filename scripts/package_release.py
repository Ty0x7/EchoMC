"""Package this standalone project without caches, worlds, models, credentials or inference runtimes."""
from pathlib import Path
import hashlib
import json
import shutil
import zipfile

root = Path(__file__).resolve().parents[1]
release = root / "dist"
release.mkdir(exist_ok=True)
version = "0.5.0"
jar = root / f"build/libs/echo-minecraft-{version}.jar"
with zipfile.ZipFile(jar) as archive:
    entries = set(archive.namelist())
    required = {"fabric.mod.json", "assets/echo/textures/entity/echo.png", "dev/echo/EchoMod.class", "dev/echo/entity/EchoEntity.class", "dev/echo/client/EchoClient.class", "dev/echo/ai/KevBackend.class", "dev/echo/ai/BoundedHttp.class"}
    assert required <= entries, required - entries
    assert {"echo.mixins.json","dev/echo/world/EchoArrival.class","dev/echo/world/EchoHearing.class","dev/echo/mixin/ServerWorldSoundMixin.class"} <= entries
    assert "dev/echo/mixin/EchoEntityLifecycleMixin.class" in entries
    assert {"dev/echo/client/EchoSetupScreen.class","dev/echo/setup/ConnectionChecks.class","dev/echo/setup/SetupCommands.class"} <= entries
    mixins=json.loads(archive.read("echo.mixins.json"))
    refmap=json.loads(archive.read(mixins["refmap"]))
    assert any("ServerWorldSoundMixin" in name for name in refmap["mappings"])
    assert any("EchoEntityLifecycleMixin" in name for name in refmap["mappings"])
    assert {"dev/echo/client/EchoCubeModel.class","dev/echo/item/EchoCubeItem.class","assets/echo/models/item/cube.json","assets/echo/textures/entity/echo_blink.png","assets/minecraft/atlases/blocks.json"} <= entries
    assert not any("dev/echo/test/" in name for name in entries), "Development game tests must not ship"
    meta = json.loads(archive.read("fabric.mod.json"))
    assert meta["version"] == version and meta["depends"]["minecraft"] == "~1.21.1"
    assert "client" in meta["entrypoints"] and "main" in meta["entrypoints"]

for name in [f"echo-minecraft-{version}.jar", f"echo-minecraft-{version}-sources.jar"]:
    shutil.copy2(root / "build/libs" / name, release / name)

source_zip = release / f"echo-minecraft-{version}-project.zip"
top_files = ["README.md", "LICENSE", ".gitignore", ".gitattributes", "build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat"]
files = [root / name for name in top_files]
for folder in ["src", "backend", "scripts", "docs", "gradle", "models", "artifacts"]:
    files.extend(p for p in (root / folder).rglob("*") if p.is_file() and "__pycache__" not in p.parts and ".venv" not in p.parts)
with zipfile.ZipFile(source_zip, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(set(files)):
        archive.write(path, "echo-minecraft/" + path.relative_to(root).as_posix())

checksums = []
for artifact in sorted(release.iterdir()):
    if artifact.name.startswith(f"echo-minecraft-{version}") and artifact.suffix in {".zip", ".jar"}:
        digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
        checksums.append(f"{digest}  {artifact.name}")
        print(f"{artifact.name}: {artifact.stat().st_size} bytes")
(release / "SHA256SUMS.txt").write_text("\n".join(checksums) + "\n", encoding="utf-8")
