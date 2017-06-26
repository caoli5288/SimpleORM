package com.mengcraft.simpleorm.lib;

import com.google.common.collect.ImmutableList;
import lombok.SneakyThrows;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

/**
 * Created on 17-6-26.
 */
public class XMLHelper {

    public static List<Element> getElementListBy(Node node, String tag) {
        if (!node.hasChildNodes()) return ImmutableList.of();
        ImmutableList.Builder<Element> b = ImmutableList.builder();
        NodeList list = node.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node item = list.item(i);
            if (item instanceof Element) {
                Element element = (Element) item;
                if (element.getTagName().equals(tag)) b.add(element);
            }
        }
        return b.build();
    }

    @SneakyThrows
    public static Document getDocumentBy(InputStream input) {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return builder.parse(input);
    }

    @SneakyThrows
    public static Document getDocumentBy(File input) {
        return getDocumentBy(new FileInputStream(input));
    }

    public static Element getElementBy(Node node, String tag) {
        if (!node.hasChildNodes()) return null;
        NodeList list = node.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node item = list.item(i);
            if (item instanceof Element) {
                Element element = (Element) item;
                if (element.getTagName().equals(tag)) return element;
            }
        }
        return null;
    }


    public static String getElementValue(Element element, String tag) {
        Element by = getElementBy(element, tag);
        if (!(by == null)) {
            return by.getFirstChild().getNodeValue();
        }
        return null;
    }

}
