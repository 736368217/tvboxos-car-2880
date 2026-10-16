"""Create an evidence-driven HTML and DOCX report without Office dependencies."""
import argparse
import base64
import hashlib
import html
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
import zipfile

from PIL import Image, ImageDraw, ImageFont

W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
A = "http://schemas.openxmlformats.org/drawingml/2006/main"
PIC = "http://schemas.openxmlformats.org/drawingml/2006/picture"
PKG = "http://schemas.openxmlformats.org/package/2006/relationships"
CT = "http://schemas.openxmlformats.org/package/2006/content-types"
for prefix, namespace in (("w", W), ("r", R), ("wp", WP), ("a", A), ("pic", PIC)):
    ET.register_namespace(prefix, namespace)


def sub(parent, ns, name, attrs=None, text=None):
    child = ET.SubElement(parent, "{%s}%s" % (ns, name), attrs or {})
    if text is not None:
        child.text = str(text)
    return child


def wattr(**values):
    return {"{%s}%s" % (W, key): str(value) for key, value in values.items()}


def paragraph(body, text="", style=None, bold=False):
    node = sub(body, W, "p")
    if style:
        sub(sub(node, W, "pPr"), W, "pStyle", wattr(val=style))
    run = sub(node, W, "r")
    if bold:
        sub(sub(run, W, "rPr"), W, "b")
    text_node = sub(run, W, "t", text=text)
    text_node.set("{http://www.w3.org/XML/1998/namespace}space", "preserve")
    return node


def field(body, instruction, text):
    node = sub(body, W, "p")
    run = sub(node, W, "r")
    sub(run, W, "fldChar", wattr(fldCharType="begin"))
    sub(sub(node, W, "r"), W, "instrText", text=instruction)
    sub(sub(node, W, "r"), W, "fldChar", wattr(fldCharType="separate"))
    sub(sub(node, W, "r"), W, "t", text=text)
    sub(sub(node, W, "r"), W, "fldChar", wattr(fldCharType="end"))


def page_break(body):
    sub(sub(sub(body, W, "p"), W, "r"), W, "br", wattr(type="page"))


def picture(body, rel, number, name, size):
    width = 6035040
    height = round(width * size[1] / size[0])
    inline = sub(sub(sub(sub(body, W, "p"), W, "r"), W, "drawing"), WP, "inline", {"distT": "0", "distB": "0", "distL": "0", "distR": "0"})
    sub(inline, WP, "extent", {"cx": str(width), "cy": str(height)})
    sub(inline, WP, "docPr", {"id": str(number), "name": name, "descr": name})
    graphic = sub(inline, A, "graphic")
    data = sub(graphic, A, "graphicData", {"uri": PIC})
    pic = sub(data, PIC, "pic")
    non = sub(pic, PIC, "nvPicPr")
    sub(non, PIC, "cNvPr", {"id": str(number), "name": name})
    sub(non, PIC, "cNvPicPr")
    fill = sub(pic, PIC, "blipFill")
    sub(fill, A, "blip", {"{%s}embed" % R: rel})
    sub(sub(fill, A, "stretch"), A, "fillRect")
    shape = sub(pic, PIC, "spPr")
    transform = sub(shape, A, "xfrm")
    sub(transform, A, "off", {"x": "0", "y": "0"})
    sub(transform, A, "ext", {"cx": str(width), "cy": str(height)})
    sub(sub(shape, A, "prstGeom", {"prst": "rect"}), A, "avLst")


def annotate(source, destination, marks):
    with Image.open(source) as raw:
        image = raw.convert("RGB")
        size = raw.size
    target = min(1600, image.width)
    scale = target / image.width
    image = image.resize((target, round(image.height * scale)), Image.Resampling.LANCZOS)
    draw = ImageDraw.Draw(image)
    font_path = Path("C:/Windows/Fonts/arialbd.ttf")
    font = ImageFont.truetype(str(font_path), 23) if font_path.exists() else ImageFont.load_default()
    for index, mark in enumerate(marks, 1):
        x1, y1, x2, y2 = [round(value * scale) for value in mark["rect"]]
        if not (0 <= x1 < x2 <= image.width and 0 <= y1 < y2 <= image.height):
            raise ValueError("Annotation outside screenshot: " + str(source))
        draw.rectangle((x1, y1, x2 - 1, y2 - 1), outline="#ffcf3b", width=4)
        cy = min(max(19, y1 + 18), image.height - 20)
        cx = min(max(19, x1 + 18), image.width - 20)
        draw.ellipse((cx - 17, cy - 17, cx + 17, cy + 17), fill="#a92d3c", outline="#ffffff", width=2)
        text = str(index)
        box = draw.textbbox((0, 0), text, font=font)
        draw.text((cx - (box[2] - box[0]) / 2, cy - (box[3] - box[1]) / 2 - box[1]), text, font=font, fill="white")
    image.save(destination, optimize=True)
    return size, image.size


def styles():
    root = ET.Element("{%s}styles" % W)
    default = sub(sub(sub(root, W, "docDefaults"), W, "rPrDefault"), W, "rPr")
    sub(default, W, "rFonts", wattr(ascii="Calibri", hAnsi="Calibri", eastAsia="Microsoft YaHei"))
    sub(default, W, "sz", wattr(val=21))
    sub(default, W, "lang", wattr(val="zh-CN", eastAsia="zh-CN"))
    for name, title, size, color in (("Normal", "Normal", 21, "243132"), ("Title", "Title", 38, "123A38"), ("Heading1", "heading 1", 29, "125B56"), ("Heading2", "heading 2", 24, "125B56"), ("Caption", "caption", 18, "55605F")):
        node = sub(root, W, "style", wattr(type="paragraph", styleId=name))
        sub(node, W, "name", wattr(val=title))
        if name == "Normal":
            node.set("{%s}default" % W, "1")
        else:
            sub(node, W, "basedOn", wattr(val="Normal"))
        properties = sub(node, W, "pPr")
        sub(properties, W, "spacing", wattr(after=110, line=300, lineRule="auto"))
        if name.startswith("Heading"):
            sub(properties, W, "outlineLvl", wattr(val=0 if name == "Heading1" else 1))
            sub(properties, W, "keepNext")
        run = sub(node, W, "rPr")
        sub(run, W, "sz", wattr(val=size))
        sub(run, W, "color", wattr(val=color))
        if name in ("Title", "Heading1", "Heading2"):
            sub(run, W, "b")
    return root


def xml_bytes(root):
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


def create_docx(path, data, pages):
    root = ET.Element("{%s}document" % W)
    body = sub(root, W, "body")
    relationships = ET.Element("{%s}Relationships" % PKG)
    content_types = ET.Element("{%s}Types" % CT)
    for extension, mime in (("rels", "application/vnd.openxmlformats-package.relationships+xml"), ("xml", "application/xml"), ("png", "image/png")):
        sub(content_types, CT, "Default", {"Extension": extension, "ContentType": mime})
    for part, kind in (("document", "document.main"), ("styles", "styles"), ("settings", "settings"), ("footer1", "footer")):
        sub(content_types, CT, "Override", {"PartName": "/word/" + part + ".xml", "ContentType": "application/vnd.openxmlformats-officedocument.wordprocessingml." + kind + "+xml"})
    sub(relationships, PKG, "Relationship", {"Id": "styles", "Type": R + "/styles", "Target": "styles.xml"})
    sub(relationships, PKG, "Relationship", {"Id": "settings", "Type": R + "/settings", "Target": "settings.xml"})
    sub(relationships, PKG, "Relationship", {"Id": "footer", "Type": R + "/footer", "Target": "footer1.xml"})
    paragraph(body, data["title"], "Title")
    paragraph(body, data["subtitle"])
    paragraph(body, data["status"], bold=True)
    paragraph(body, "APK SHA-256: " + data["apk_sha256"], "Caption")
    for line in data["summary"]:
        paragraph(body, line)
    paragraph(body, data.get("contents_label", "Contents"), "Heading1")
    field(body, ' TOC \\o "1-2" \\h \\z \\u ', " / ".join(page["title"] for page in pages))
    page_break(body)
    paragraph(body, data["verification_title"], "Heading1")
    for item in data["checks"]:
        paragraph(body, item["name"] + ": " + item["result"], bold=True)
        if item.get("detail"):
            paragraph(body, item["detail"])
    for number, page in enumerate(pages, 1):
        page_break(body)
        paragraph(body, str(number) + ". " + page["title"], "Heading1")
        paragraph(body, page.get("context", ""))
        relationship = "image" + str(number)
        sub(relationships, PKG, "Relationship", {"Id": relationship, "Type": R + "/image", "Target": "media/" + page["image"].name})
        picture(body, relationship, number, page["title"], page["image_size"])
        paragraph(body, page["caption"], "Caption")
        for index, mark in enumerate(page["marks"], 1):
            paragraph(body, str(index) + ". " + mark["text"], bold=True)
        for line in page.get("notes", []):
            paragraph(body, line)
    page_break(body)
    paragraph(body, data["limits_title"], "Heading1")
    for line in data["limitations"]:
        paragraph(body, line)
    paragraph(body, data["evidence_title"], "Heading1")
    for line in data["evidence"]:
        paragraph(body, line)
    section = sub(body, W, "sectPr")
    sub(section, W, "footerReference", {"{%s}type" % W: "default", "{%s}id" % R: "footer"})
    sub(section, W, "pgSz", wattr(w=11906, h=16838))
    sub(section, W, "pgMar", wattr(top=1000, right=1000, bottom=1000, left=1000, header=400, footer=400, gutter=0))
    settings = ET.Element("{%s}settings" % W)
    sub(settings, W, "updateFields", wattr(val="true"))
    footer = ET.Element("{%s}ftr" % W)
    field(footer, " PAGE ", "1")
    root_rels = ET.Element("{%s}Relationships" % PKG)
    sub(root_rels, PKG, "Relationship", {"Id": "officeDocument", "Type": R + "/officeDocument", "Target": "word/document.xml"})
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        for name, node in (("[Content_Types].xml", content_types), ("_rels/.rels", root_rels), ("word/document.xml", root), ("word/styles.xml", styles()), ("word/settings.xml", settings), ("word/footer1.xml", footer), ("word/_rels/document.xml.rels", relationships)):
            archive.writestr(name, xml_bytes(node))
        for page in pages:
            archive.write(page["image"], "word/media/" + page["image"].name)
    with zipfile.ZipFile(path) as archive:
        if archive.testzip():
            raise ValueError("Invalid DOCX ZIP")
        for name in archive.namelist():
            if name.endswith((".xml", ".rels")):
                ET.fromstring(archive.read(name))
        embedded = [name for name in archive.namelist() if name.startswith("word/media/")]
        if len(embedded) != len(pages):
            raise ValueError("DOCX image count mismatch")


def create_html(path, data, pages):
    esc = html.escape
    blocks = ["<!doctype html><html lang='zh-CN'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>", "<title>" + esc(data["title"]) + "</title><style>body{margin:0;color:#263534;background:#f4f6f5;font:16px/1.7 'Microsoft YaHei',Arial,sans-serif;letter-spacing:0}main{max-width:1100px;margin:auto;padding:36px 28px;background:white}h1{font-size:29px;margin:0;color:#154c46}h2{font-size:23px;color:#154c46;margin:30px 0 12px}h3{font-size:18px}p{margin:10px 0}.status{border-left:5px solid #ae3448;padding:10px 16px;background:#fbedef}.sha{font:12px/1.7 Consolas,monospace;overflow-wrap:anywhere}.page{break-before:page;border-top:1px solid #ccd8d4;padding-top:20px;margin-top:30px}img{width:100%;height:auto;display:block}figcaption{font-size:13px;color:#596a65}figure{margin:15px 0}.mark{font-weight:bold;color:#8a2537}.check{border-bottom:1px solid #e0e6e3;padding:9px 0}nav{font-size:14px}a{color:#125b56}code{overflow-wrap:anywhere}li{margin:7px 0}@media print{body{background:white;font-size:11pt}main{padding:0;max-width:none}h1{font-size:22pt}h2{font-size:17pt}figure,li{break-inside:avoid}nav{display:none}@page{size:A4;margin:15mm}}@media(max-width:600px){main{padding:22px 14px}h1{font-size:24px}h2{font-size:21px}}</style><main>"]
    blocks += ["<h1>" + esc(data["title"]) + "</h1><p>" + esc(data["subtitle"]) + "</p>", "<p class='status'>" + esc(data["status"]) + "</p><p class='sha'>APK SHA-256: " + esc(data["apk_sha256"]) + "</p>"]
    blocks += ["<p>" + esc(line) + "</p>" for line in data["summary"]]
    blocks.append("<nav>" + " | ".join("<a href='#page-%d'>%s</a>" % (i, esc(page["title"])) for i, page in enumerate(pages, 1)) + "</nav>")
    blocks.append("<h2>" + esc(data["verification_title"]) + "</h2>")
    for item in data["checks"]:
        blocks.append("<div class='check'><b>" + esc(item["name"] + ": " + item["result"]) + "</b><br>" + esc(item.get("detail", "")) + "</div>")
    for index, page in enumerate(pages, 1):
        encoded = base64.b64encode(page["image"].read_bytes()).decode("ascii")
        blocks.append("<section class='page' id='page-%d'><h2>%d. %s</h2><p>%s</p><figure><img alt='%s' src='data:image/png;base64,%s'><figcaption>%s</figcaption></figure>" % (index, index, esc(page["title"]), esc(page.get("context", "")), esc(page["title"]), encoded, esc(page["caption"])))
        blocks.append("<ol class='mark'>" + "".join("<li>" + esc(mark["text"]) + "</li>" for mark in page["marks"]) + "</ol>")
        blocks += ["<p>" + esc(line) + "</p>" for line in page.get("notes", [])]
        blocks.append("</section>")
    for title, lines in ((data["limits_title"], data["limitations"]), (data["evidence_title"], data["evidence"])):
        blocks.append("<section class='page'><h2>" + esc(title) + "</h2>" + "".join("<p>" + esc(line) + "</p>" for line in lines) + "</section>")
    blocks.append("</main></html>")
    path.write_text("\n".join(blocks), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    data = json.loads(args.manifest.read_text(encoding="utf-8-sig"))
    if "pages" not in data:
        data["pages"] = json.loads((args.manifest.parent / data["pages_file"]).read_text(encoding="utf-8-sig"))
    if not re.fullmatch(r"[a-fA-F0-9]{64}", data["apk_sha256"]):
        parser.error("APK SHA-256 is required")
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    images = out / "images"
    images.mkdir(exist_ok=True)
    pages = []
    for number, original in enumerate(data["pages"], 1):
        page = original.copy()
        source = (args.manifest.parent / page["source"]).resolve()
        destination = images / ("page-%02d.png" % number)
        raw_size, image_size = annotate(source, destination, page["marks"])
        page.update(image=destination, image_size=image_size)
        page["raw_sha256"] = hashlib.sha256(source.read_bytes()).hexdigest()
        page["raw_size"] = raw_size
        pages.append(page)
    create_html(out / "TVBox-car7-report.html", data, pages)
    create_docx(out / "TVBox-car7-report.docx", data, pages)
    checks = {"page_count": len(pages), "docx_xml_valid": True, "docx_images": len(pages), "html_images": len(pages), "apk_sha256": data["apk_sha256"], "screenshots": [{"title": page["title"], "raw_sha256": page["raw_sha256"], "size": page["raw_size"]} for page in pages]}
    (out / "report-validation.json").write_text(json.dumps(checks, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in checks.items() if key != "screenshots"}))


if __name__ == "__main__":
    main()
