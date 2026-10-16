"""Check report structure and desktop/mobile HTML without relying on the old workspace."""
import argparse
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

from PIL import Image, ImageStat
from pypdf import PdfReader
from playwright.sync_api import sync_playwright


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True, help="Scratch directory for browser validation images")
    parser.add_argument("--pages", type=Path, default=Path(__file__).resolve().parents[1] / "reports/car.7/pages.json")
    args = parser.parse_args()
    report = args.report.resolve()
    args.out.mkdir(parents=True, exist_ok=True)
    pages = json.loads(args.pages.read_text(encoding="utf-8"))
    reader = PdfReader(report / "TVBox-car7-report.pdf")
    texts = [page.extract_text() or "" for page in reader.pages]
    normalize = lambda text: re.sub(r"\s+", "", text)
    joined = normalize("\n".join(texts))
    if any(normalize(page["title"]) not in joined for page in pages):
        raise ValueError("Missing PDF section title")
    image_pages = [index + 1 for index, page in enumerate(reader.pages) if len(page.images)]
    if len(image_pages) != len(pages) or any(len(normalize(text)) <= 15 for text in texts):
        raise ValueError("Missing screenshot page or blank PDF page")
    with zipfile.ZipFile(report / "TVBox-car7-report.docx") as archive:
        if archive.testzip():
            raise ValueError("DOCX CRC failure")
        for name in archive.namelist():
            if name.endswith((".xml", ".rels")):
                ET.fromstring(archive.read(name))
        document = archive.read("word/document.xml")
        if b"TOC" not in document:
            raise ValueError("Missing TOC field")
        images = [name for name in archive.namelist() if name.startswith("word/media/")]
        ns = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
        w = "{" + ns["w"] + "}"
        styles = ET.fromstring(archive.read("word/styles.xml"))
        headings = {node.get(w + "styleId") for node in styles.findall("w:style", ns) if node.find("w:pPr/w:outlineLvl", ns) is not None and node.find("w:pPr/w:outlineLvl", ns).get(w + "val") == "0"}
        count = sum(node.get(w + "val") in headings for node in ET.fromstring(document).findall(".//w:pStyle", ns))
        if count < len(pages) or len(images) != len(pages):
            raise ValueError("DOCX headings/images incomplete")
    checks = {"pdf_pages": len(reader.pages), "pdf_screenshot_pages": image_pages, "pdf_all_titles_present": True,
              "pdf_nonblank_pages": True, "docx_images": len(images), "docx_xml_valid": True, "docx_heading_styles_and_toc": True, "viewports": []}
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(channel="msedge", headless=True)
        try:
            for width, height in [(1440, 1000), (390, 844)]:
                page = browser.new_page(viewport={"width": width, "height": height}, device_scale_factor=1)
                try:
                    page.goto((report / "TVBox-car7-report.html").as_uri(), wait_until="load")
                    page.evaluate("document.fonts.ready")
                    state = page.evaluate("() => ({overflow:document.documentElement.scrollWidth>innerWidth+1,loaded:[...document.images].filter(i=>i.complete&&i.naturalWidth>0).length})")
                    if state["overflow"] or state["loaded"] != len(pages):
                        raise ValueError("HTML overflow or missing image")
                    page.screenshot(path=str(args.out / ("cover-%d.png" % width)))
                    for index in [1, 4, 19, 20, 21, 22]:
                        path = args.out / ("page-%02d-%d.png" % (index, width))
                        page.locator("#page-%d" % index).screenshot(path=str(path))
                        with Image.open(path) as image:
                            if max(ImageStat.Stat(image.convert("RGB")).stddev) <= 10:
                                raise ValueError("Blank browser screenshot")
                    checks["viewports"].append({"width": width, "height": height, "images_loaded": len(pages), "horizontal_overflow": False, "sampled_pages_nonblank": True})
                finally:
                    page.close()
        finally:
            browser.close()
    (report / "document-validation.json").write_text(json.dumps(checks, indent=2), encoding="utf-8")
    print(json.dumps(checks))


if __name__ == "__main__":
    main()
