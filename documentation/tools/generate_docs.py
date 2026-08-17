from pathlib import Path

ROOT = Path(__file__).resolve().parent
PARTS = sorted(ROOT.glob("generator_source.part*"))
if not PARTS:
    raise RuntimeError("Documentation generator source parts are missing")
SOURCE = "".join(path.read_text(encoding="utf-8") for path in PARTS)
VIRTUAL_FILE = ROOT / "generator_source.py"
exec(compile(SOURCE, str(VIRTUAL_FILE), "exec"), {"__name__": "__main__", "__file__": str(VIRTUAL_FILE), "__package__": None})
