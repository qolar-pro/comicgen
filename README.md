# ComicGen Studio

A local-first Flask app that lets you:

1. Upload photos and convert them into reusable comic characters (stored locally).
2. Type location, event, and villain details.
3. Generate a full comic-page prompt artifact (for Imagen integration).
4. Generate a soundtrack brief artifact (Gemini-driven text output).

## Quickstart

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
python app.py
```

Then open `http://127.0.0.1:5000`.

## Notes on APIs

- Character extraction uses OpenAI vision model if `OPENAI_API_KEY` is set.
- Comic and music generation use Gemini text generation to create production-ready artifacts.
- The Imagen binary image generation endpoint can differ by account/region, so this starter stores the generated prompt in `data/comics/` ready to plug into your specific Imagen endpoint.

## Local data

- `data/characters/` uploaded photos
- `data/characters.json` character library
- `data/comics/` generated comic prompt artifacts
- `data/music/` generated soundtrack briefs
