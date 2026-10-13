"""Fill the App Store Connect record of Ilmerya (version 1.0) from store-assets/listing.md.

Idempotent: existing localizations are patched, missing ones created. Never prints keys.
Usage: python tools/asc_metadata.py [--dry-run]
"""
import re, sys
from pathlib import Path
import requests
sys.path.insert(0, str(Path(__file__).parent))
import asc_signing as asc

APP = "6819181162"
SITE = "https://ilmerya-league.onrender.com"
LISTING = Path(__file__).parent.parent / "store-assets" / "listing.md"
DRY = "--dry-run" in sys.argv


def call(method, path, body=None, params=None):
    if DRY and method != "GET":
        print("DRY", method, path); return {}
    for attempt in range(4):
        try:
            r = requests.request(method, asc.API + path, headers={"Authorization": f"Bearer {asc.token()}"},
                                 json=body, params=params, timeout=60)
            break
        except requests.exceptions.ConnectionError:
            if attempt == 3: raise
    if r.status_code >= 400:
        errors = [(e.get("code"), e.get("detail")) for e in r.json().get("errors", [])] if r.content else []
        raise SystemExit(f"{method} {path} -> HTTP {r.status_code}: {errors}")
    return r.json() if r.content else {}


def section(lang):
    text = LISTING.read_text(encoding="utf-8")
    block = text.split(f"## {lang}", 1)[1].split("\n## ", 1)[0]
    description = block.split(":**", 3)[-1].strip() if False else re.split(r"\*\*(?:Tam açıklama|Full description):\*\*", block)[1].strip()
    return description


# Store-facing text per App Store locale. Names/subtitles are limited to 30 characters,
# keywords to 100 characters, promotional text to 170 characters.
LOCALES = {
    "tr": dict(
        name="İlmerya: Taş Birleştirme",
        subtitle="Altıgen taşlarla zincir kur",
        keywords="bulmaca,birleştirme,altıgen,taş,zincir,sayı,mantık,strateji,lig,merge,sakin,2048",
        promotionalText="Ceviz tablada doğal taşları buluştur, zincir dalgaları kur ve Devir ile sütunu çevir. Haftalık ligde Game Center ile yarış.",
        description=section("Türkçe (tr-TR)"),
    ),
    "en-US": dict(
        name="Ilmerya: Stone Merge Puzzle",
        subtitle="Merge stones, build cascades",
        keywords="puzzle,merge,hexagon,stone,chain,number,logic,strategy,league,calm,2048,match",
        promotionalText="Connect natural stones on a walnut board, build cascade waves and Cycle a column. Compete in the weekly Game Center league.",
        description=section("English (en-US)"),
    ),
}


def main():
    for loc in LOCALES.values():
        assert len(loc["name"]) <= 30 and len(loc["subtitle"]) <= 30, loc["name"]
        assert len(loc["keywords"]) <= 100 and len(loc["promotionalText"]) <= 170, loc["keywords"]
        assert len(loc["description"]) <= 4000

    info = call("GET", f"/apps/{APP}/appInfos")["data"][0]
    version = [v for v in call("GET", f"/apps/{APP}/appStoreVersions")["data"]
               if v["attributes"]["appStoreState"] == "PREPARE_FOR_SUBMISSION"][0]

    # App-level information: name, subtitle, privacy policy.
    existing = {d["attributes"]["locale"]: d["id"] for d in call("GET", f"/appInfos/{info['id']}/appInfoLocalizations")["data"]}
    for locale, loc in LOCALES.items():
        attrs = {"name": loc["name"], "subtitle": loc["subtitle"], "privacyPolicyUrl": f"{SITE}/privacy"}
        if locale in existing:
            call("PATCH", f"/appInfoLocalizations/{existing[locale]}",
                 {"data": {"type": "appInfoLocalizations", "id": existing[locale], "attributes": attrs}})
        else:
            call("POST", "/appInfoLocalizations", {"data": {"type": "appInfoLocalizations", "attributes": {"locale": locale, **attrs},
                 "relationships": {"appInfo": {"data": {"type": "appInfos", "id": info["id"]}}}}})
        print("app info", locale, "ok")

    # Category: Games > Puzzle, secondary Games > Board.
    call("PATCH", f"/appInfos/{info['id']}", {"data": {"type": "appInfos", "id": info["id"], "relationships": {
        "primaryCategory": {"data": {"type": "appCategories", "id": "GAMES"}},
        "primarySubcategoryOne": {"data": {"type": "appCategories", "id": "GAMES_PUZZLE"}},
        "primarySubcategoryTwo": {"data": {"type": "appCategories", "id": "GAMES_BOARD"}}}}})
    print("category ok")

    # Version text.
    existing = {d["attributes"]["locale"]: d["id"] for d in call("GET", f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations")["data"]}
    for locale, loc in LOCALES.items():
        attrs = {"description": loc["description"], "keywords": loc["keywords"], "promotionalText": loc["promotionalText"],
                 "supportUrl": f"{SITE}/support", "marketingUrl": f"{SITE}/"}
        if locale in existing:
            call("PATCH", f"/appStoreVersionLocalizations/{existing[locale]}",
                 {"data": {"type": "appStoreVersionLocalizations", "id": existing[locale], "attributes": attrs}})
        else:
            call("POST", "/appStoreVersionLocalizations", {"data": {"type": "appStoreVersionLocalizations",
                 "attributes": {"locale": locale, **attrs},
                 "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": version["id"]}}}}})
        print("version text", locale, "ok")

    call("PATCH", f"/appStoreVersions/{version['id']}", {"data": {"type": "appStoreVersions", "id": version["id"],
         "attributes": {"copyright": "2026 OzGAMES", "releaseType": "AFTER_APPROVAL"}}})
    call("PATCH", f"/apps/{APP}", {"data": {"type": "apps", "id": APP,
         "attributes": {"contentRightsDeclaration": "DOES_NOT_USE_THIRD_PARTY_CONTENT"}}})
    print("version, content rights ok")

    # Age rating: no objectionable content, no gambling, no user-generated content or chat, ads present.
    rating = call("GET", f"/appInfos/{info['id']}/ageRatingDeclaration")["data"]
    none = "NONE"
    attrs = {k: none for k in ["alcoholTobaccoOrDrugUseOrReferences", "contests", "gamblingSimulated", "horrorOrFearThemes",
             "matureOrSuggestiveThemes", "medicalOrTreatmentInformation", "profanityOrCrudeHumor",
             "sexualContentGraphicAndNudity", "sexualContentOrNudity", "violenceCartoonOrFantasy",
             "violenceRealistic", "violenceRealisticProlongedGraphicOrSadistic", "gunsOrOtherWeapons"]}
    attrs.update({"gambling": False, "unrestrictedWebAccess": False, "lootBox": False, "messagingAndChat": False,
                  "userGeneratedContent": False, "advertising": True, "parentalControls": False,
                  "ageAssurance": False, "healthOrWellnessTopics": False})
    try:
        call("PATCH", f"/ageRatingDeclarations/{rating['id']}", {"data": {"type": "ageRatingDeclarations", "id": rating["id"], "attributes": attrs}})
    except SystemExit as e:
        # Older API schemas reject newer keys; retry with the classic set only.
        print("age rating full set rejected:", e)
        for k in ["lootBox", "messagingAndChat", "userGeneratedContent", "advertising", "parentalControls",
                  "ageAssurance", "healthOrWellnessTopics", "gunsOrOtherWeapons"]:
            attrs.pop(k, None)
        call("PATCH", f"/ageRatingDeclarations/{rating['id']}", {"data": {"type": "ageRatingDeclarations", "id": rating["id"], "attributes": attrs}})
    print("age rating ok")


if __name__ == "__main__":
    main()
