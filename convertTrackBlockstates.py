import json
from pathlib import Path


INPUT_FOLDER = Path("blockstates")
OUTPUT_FOLDER = Path("output_json")

RENDERER_VALUE = "create:track"

TARGET_SUFFIXES = (
    "/x_ortho",
    "/z_ortho",
    "/ascending",
    "/diag",
    "/diag_2",
)

OUTPUT_FOLDER.mkdir(parents=True, exist_ok=True)


# Process all JSON files
for input_file in INPUT_FOLDER.glob("*.json"):

    with open(input_file, "r", encoding="utf-8") as f:
        data = json.load(f)

    variants = data.get("variants", {})

    for variant in variants.values():
        model = variant.get("model", "")

        if model.endswith(TARGET_SUFFIXES):
            variant["renderer"] = RENDERER_VALUE

    # Save with same filename
    output_file = OUTPUT_FOLDER / input_file.name

    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)

    print(f"Processed: {input_file.name}")