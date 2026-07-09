package org.makar.ocrapp.screenobjectsextractor.model.infrastructure.ai.ocr.tess4j.v2;

import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObject.OcrLevel;
import org.makar.ocrapp.screenobjectsextractor.model.common.entities.TextObjects;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Rectangle;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбирает hOCR-XML, выдаваемый Tesseract, в плоский список {@link TextObject}.
 *
 * <p>Иерархия hOCR:
 * <pre>
 *   div.ocr_page
 *     div.ocr_carea      → BLOCK
 *       p.ocr_par        → PARAGRAPH
 *         span.ocr_line  → LINE
 *           span.ocrx_word → WORD  (несёт x_wconf)
 *             span.ocrx_cinfo → SYMBOL
 * </pre>
 *
 * <p>Входные данные — строка XML. Никакой зависимости от Tesseract API нет,
 * поэтому класс легко тестируется с любым заранее подготовленным hOCR-фрагментом.
 */
class HocrParser {

    private static final Logger logger = Logger.getLogger(HocrParser.class.getName());

    private static final Pattern BBOX_PATTERN =
            Pattern.compile("bbox (\\d+) (\\d+) (\\d+) (\\d+)");
    private static final Pattern CONF_PATTERN =
            Pattern.compile("x_wconf (\\d+)");

    /** Минимальный порог confidence (0–100): слова ниже считаются шумом. */
    private static final float MIN_CONFIDENCE = 30.0f;

    /**
     * Допуск сравнения координат bounding box в пикселях при поглощении дублей.
     * Tesseract может давать bbox ±1–2 px для одного региона на разных уровнях.
     */
    private static final int BBOX_TOLERANCE = 2;

    // ── Публичный API ────────────────────────────────────────────────────

    /**
     * Разбирает hOCR-XML в {@link TextObjects}.
     *
     * @param hocrXml строка, полученная от {@code Tesseract.doOCR()} в режиме hOCR
     * @return плоский список {@link TextObject} всех уровней иерархии,
     *         очищенный от геометрических дублей
     */
    TextObjects parse(String hocrXml) {
        TextObjects result = new TextObjects();

        Document doc = buildDocument(hocrXml);
        if (doc == null) return result;

        extractLevel(doc, "ocr_carea",  OcrLevel.BLOCK,     false, result);
        extractLevel(doc, "ocr_par",    OcrLevel.PARAGRAPH, false, result);
        extractLevel(doc, "ocr_line",   OcrLevel.LINE,      false, result);
        extractLevel(doc, "ocrx_word",  OcrLevel.WORD,      true,  result);
        extractLevel(doc, "ocrx_cinfo", OcrLevel.SYMBOL,    false, result);

        result.setTextObjectList(absorb(result.getTextObjectList()));
        return result;
    }

    // ── XML-парсинг ───────────────────────────────────────────────────────

    private static Document buildDocument(String hocrXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities",  false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            // Пустой EntityResolver: не даёт парсеру лезти в сеть за DTD w3.org
            builder.setEntityResolver((pub, sys) -> new InputSource(new StringReader("")));
            return builder.parse(new InputSource(new StringReader(hocrXml)));

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to parse hOCR XML: " + e.getMessage(), e);
            return null;
        }
    }

    private void extractLevel(Document doc,
                              String hocrClass,
                              OcrLevel level,
                              boolean filterByConf,
                              TextObjects target) {
        List<Element> elements = getElementsByClass(doc, hocrClass);
        int skipped = 0;

        for (Element el : elements) {
            String title = el.getAttribute("title");
            Rectangle bbox = parseBbox(title);
            if (bbox == null) {
                logger.log(Level.FINE, "No bbox for {0}, skipping", hocrClass);
                continue;
            }

            String text = extractDirectText(el).strip();
            if (text.isBlank()) { skipped++; continue; }

            if (filterByConf) {
                float conf = parseConfidence(title);
                if (conf < MIN_CONFIDENCE) {
                    skipped++;
                    logger.log(Level.FINE, String.format(
                            "Skipping low-conf %s '%s' (%.0f%% < %.0f%%)",
                            hocrClass, text, conf, MIN_CONFIDENCE));
                    continue;
                }
            }

            target.addTextObject(new TextObject(text, bbox.x, bbox.y, bbox.width, bbox.height, level));
        }

        logger.log(Level.FINE, String.format(
                "Level %-12s: total=%d, skipped=%d",
                level.name(), target.getTextObjectList().size(), skipped));
    }

    private static List<Element> getElementsByClass(Document doc, String className) {
        List<Element> result = new ArrayList<>();
        for (String tag : new String[]{"span", "div", "p"}) {
            NodeList nodes = doc.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                if (className.equals(el.getAttribute("class"))) result.add(el);
            }
        }
        return result;
    }

    private static Rectangle parseBbox(String title) {
        if (title == null) return null;
        Matcher m = BBOX_PATTERN.matcher(title);
        if (!m.find()) return null;
        int x0 = Integer.parseInt(m.group(1)), y0 = Integer.parseInt(m.group(2));
        int x1 = Integer.parseInt(m.group(3)), y1 = Integer.parseInt(m.group(4));
        return new Rectangle(x0, y0, x1 - x0, y1 - y0);
    }

    private static float parseConfidence(String title) {
        if (title == null) return 0.0f;
        Matcher m = CONF_PATTERN.matcher(title);
        return m.find() ? Float.parseFloat(m.group(1)) : 0.0f;
    }

    private static String extractDirectText(Element el) {
        StringBuilder sb = new StringBuilder();
        org.w3c.dom.NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
                sb.append(child.getNodeValue());
            } else if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                sb.append(extractDirectText((Element) child));
            }
        }
        return sb.toString();
    }

    // ── Поглощение дублей ─────────────────────────────────────────────────

    /**
     * Удаляет из плоского списка объекты, которые дублируются объектом
     * более высокого уровня иерархии (меньший {@code tesseractLevel} — победитель).
     */
    private static List<TextObject> absorb(List<TextObject> raw) {
        Set<Integer> absorbed = new HashSet<>();

        for (int i = 0; i < raw.size(); i++) {
            if (absorbed.contains(i)) continue;
            TextObject dominant = raw.get(i);

            for (int j = i + 1; j < raw.size(); j++) {
                if (absorbed.contains(j)) continue;
                TextObject candidate = raw.get(j);

                if (!isDuplicate(dominant, candidate)) continue;

                if (dominant.getLevel().tesseractLevel <= candidate.getLevel().tesseractLevel) {
                    absorbed.add(j);
                    logger.log(Level.FINE, String.format("Absorbed %s '%s' by %s",
                            candidate.getLevel(), candidate.getText(), dominant.getLevel()));
                } else {
                    absorbed.add(i);
                    logger.log(Level.FINE, String.format("Absorbed %s '%s' by %s",
                            dominant.getLevel(), dominant.getText(), candidate.getLevel()));
                    break;
                }
            }
        }

        List<TextObject> result = new ArrayList<>(raw.size() - absorbed.size());
        for (int i = 0; i < raw.size(); i++) {
            if (!absorbed.contains(i)) result.add(raw.get(i));
        }

        logger.log(Level.INFO, String.format("Absorption: %d → %d (removed %d)",
                raw.size(), result.size(), absorbed.size()));
        return result;
    }

    private static boolean isDuplicate(TextObject a, TextObject b) {
        String tA = a.getText().replaceAll("\\s+", " ").strip();
        String tB = b.getText().replaceAll("\\s+", " ").strip();
        if (!tA.equalsIgnoreCase(tB)) return false;

        return Math.abs(a.getX()      - b.getX())      <= BBOX_TOLERANCE
                && Math.abs(a.getY()      - b.getY())      <= BBOX_TOLERANCE
                && Math.abs(a.getWidth()  - b.getWidth())  <= BBOX_TOLERANCE
                && Math.abs(a.getHeight() - b.getHeight()) <= BBOX_TOLERANCE;
    }
}
