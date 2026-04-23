import base64
import json
import os
import uuid
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Dict, List

from dotenv import load_dotenv
from flask import Flask, jsonify, render_template, request
from PIL import Image

load_dotenv()

ROOT = Path(__file__).parent
DATA_DIR = ROOT / "data"
CHAR_IMG_DIR = DATA_DIR / "characters"
COMIC_DIR = DATA_DIR / "comics"
MUSIC_DIR = DATA_DIR / "music"
CHAR_DB = DATA_DIR / "characters.json"

for p in [DATA_DIR, CHAR_IMG_DIR, COMIC_DIR, MUSIC_DIR]:
    p.mkdir(parents=True, exist_ok=True)

if not CHAR_DB.exists():
    CHAR_DB.write_text("[]", encoding="utf-8")

app = Flask(__name__)


@dataclass
class Character:
    id: str
    name: str
    description: str
    style_notes: str
    image_path: str


def load_characters() -> List[Character]:
    raw = json.loads(CHAR_DB.read_text(encoding="utf-8"))
    return [Character(**c) for c in raw]


def save_characters(characters: List[Character]) -> None:
    CHAR_DB.write_text(
        json.dumps([asdict(c) for c in characters], indent=2), encoding="utf-8"
    )


def to_data_url(path: Path) -> str:
    mime = "image/png" if path.suffix.lower() == ".png" else "image/jpeg"
    b64 = base64.b64encode(path.read_bytes()).decode("utf-8")
    return f"data:{mime};base64,{b64}"


def describe_character_with_openai(image_path: Path, user_hint: str) -> Dict[str, str]:
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        return {
            "name": "Unknown Hero",
            "description": "A confident character inferred from the uploaded photo.",
            "style_notes": (
                "comic-book inks, expressive eyes, clean outlines, "
                "consistent hairstyle and outfit"
            ),
        }

    from openai import OpenAI

    client = OpenAI(api_key=api_key)
    data_url = to_data_url(image_path)

    response = client.responses.create(
        model="gpt-4.1-mini",
        input=[
            {
                "role": "user",
                "content": [
                    {
                        "type": "text",
                        "text": (
                            "You are extracting a comic character profile from a person image. "
                            "Return strict JSON with keys name, description, style_notes. "
                            f"User hint: {user_hint or 'none'}"
                        ),
                    },
                    {"type": "image_url", "image_url": data_url},
                ],
            }
        ],
        text={"format": {"type": "json_object"}},
    )

    return json.loads(response.output_text)


def generate_comic_with_google(prompt: str) -> str:
    api_key = os.getenv("GOOGLE_API_KEY")
    if not api_key:
        placeholder = COMIC_DIR / f"comic_{uuid.uuid4().hex}.txt"
        placeholder.write_text(
            "Set GOOGLE_API_KEY and wire Imagen generation to output real images.\n"
            + prompt,
            encoding="utf-8",
        )
        return str(placeholder.relative_to(ROOT))

    import google.generativeai as genai

    genai.configure(api_key=api_key)
    model = genai.GenerativeModel("gemini-1.5-pro")
    result = model.generate_content(
        "Create a concise, production-ready Imagen prompt for a full comic page: " + prompt
    )
    imagen_prompt = result.text.strip()

    # NOTE: Imagen binary-generation APIs vary by account/region.
    # This stores the final prompt so you can plug in your preferred Imagen endpoint.
    prompt_file = COMIC_DIR / f"comic_prompt_{uuid.uuid4().hex}.txt"
    prompt_file.write_text(imagen_prompt, encoding="utf-8")
    return str(prompt_file.relative_to(ROOT))


def generate_music_with_gemini(story_context: str) -> str:
    api_key = os.getenv("GOOGLE_API_KEY")
    out = MUSIC_DIR / f"track_{uuid.uuid4().hex}.txt"

    if not api_key:
        out.write_text(
            "No GOOGLE_API_KEY set. Placeholder soundtrack brief:\n"
            f"{story_context}",
            encoding="utf-8",
        )
        return str(out.relative_to(ROOT))

    import google.generativeai as genai

    genai.configure(api_key=api_key)
    model = genai.GenerativeModel("gemini-1.5-pro")
    result = model.generate_content(
        "Create a short soundtrack specification for comic background music. "
        "Include mood, bpm range, instrument palette, and transitions.\n"
        + story_context
    )
    out.write_text(result.text or "No output", encoding="utf-8")
    return str(out.relative_to(ROOT))


@app.route("/")
def index():
    return render_template("index.html", characters=load_characters())


@app.route("/api/characters", methods=["GET"])
def get_characters():
    return jsonify([asdict(c) for c in load_characters()])


@app.route("/api/characters", methods=["POST"])
def add_character():
    image = request.files.get("image")
    name_override = (request.form.get("name") or "").strip()
    hint = (request.form.get("hint") or "").strip()

    if image is None:
        return jsonify({"error": "image is required"}), 400

    ext = Path(image.filename or "upload.jpg").suffix or ".jpg"
    char_id = uuid.uuid4().hex
    image_path = CHAR_IMG_DIR / f"{char_id}{ext}"
    image.save(image_path)

    # Normalize image size for consistent prompt quality.
    with Image.open(image_path) as img:
        img.thumbnail((1024, 1024))
        img.save(image_path)

    profile = describe_character_with_openai(image_path, hint)

    character = Character(
        id=char_id,
        name=name_override or profile.get("name", "Unnamed Character"),
        description=profile.get("description", ""),
        style_notes=profile.get("style_notes", ""),
        image_path=str(image_path.relative_to(ROOT)),
    )

    characters = load_characters()
    characters.append(character)
    save_characters(characters)
    return jsonify(asdict(character)), 201


@app.route("/api/generate", methods=["POST"])
def generate():
    payload = request.get_json(force=True)
    selected_ids = payload.get("character_ids", [])
    location = (payload.get("location") or "").strip()
    event = (payload.get("event") or "").strip()
    villain = (payload.get("villain") or "").strip()

    chars = [c for c in load_characters() if c.id in selected_ids]
    if not chars:
        return jsonify({"error": "select at least one character"}), 400

    cast = "\n".join(
        [f"- {c.name}: {c.description}. Style notes: {c.style_notes}" for c in chars]
    )
    story_context = (
        f"Location: {location}\n"
        f"Event: {event}\n"
        f"Villain: {villain}\n"
        f"Cast:\n{cast}\n"
        "Output: single comic page, 5-8 panels, cinematic lighting, speech bubbles space, "
        "consistent character appearance across panels."
    )

    comic_artifact = generate_comic_with_google(story_context)
    music_artifact = generate_music_with_gemini(story_context)

    return jsonify({
        "comic_artifact": comic_artifact,
        "music_artifact": music_artifact,
        "story_context": story_context,
    })


if __name__ == "__main__":
    app.run(debug=True)
