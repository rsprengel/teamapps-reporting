package org.teamapps.reporting.builder;


import org.docx4j.XmlUtils;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.*;
import org.jvnet.jaxb2_commons.ppp.Child;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;

public class DocumentBuilder {

	public Map<String, String> createReplaceRowMap(String ... values) {
		Map<String, String> map = new HashMap<>();
		for (int i = 0; i < values.length; i += 2) {
			map.put(values[i], values[i+1]);
		}
		return map;
	}

	public WordprocessingMLPackage getTemplate(String path) throws Docx4JException, FileNotFoundException {
		return WordprocessingMLPackage.load(new FileInputStream(new File(path)));
	}

	public <T> T copyElement(T t) {
		return XmlUtils.deepCopy(t);
	}

	public <T> List<T> getAllElements(Object element, T toSearch) {
		List<T> result = new ArrayList<>();
		if (element instanceof JAXBElement) element = ((JAXBElement<?>) element).getValue();

		if (element.getClass().equals(toSearch.getClass()))
			result.add((T) element);
		else if (element instanceof ContentAccessor) {
			List<?> children = ((ContentAccessor) element).getContent();
			for (Object child : children) {
				result.addAll(getAllElements(child, toSearch));
			}
		}
		return result;
	}

	public void save(WordprocessingMLPackage template, String target) throws Docx4JException {
		File f = new File(target);
		template.save(f);
	}

	public void fillTable(List<Map<String, String>> textToAdd, WordprocessingMLPackage template, String ... keys) throws Docx4JException, JAXBException {
		fillTable(textToAdd, template, Arrays.asList(keys));
	}

	public void fillTable(List<Map<String, String>> textToAdd, WordprocessingMLPackage template, List<String> keys) throws Docx4JException, JAXBException {
		List<Tbl> tables = getAllElements(template.getMainDocumentPart(), new Tbl());
		Tbl matchingTable = null;
		for (Tbl table : tables) {
			boolean hit = true;
			for (String key : keys) {
				if (getParagraphWithText(table, key) == null) {
					hit = false;
					break;
				}
			}
			if (hit) {
				matchingTable = table;
				break;
			}
		}

		Set<Tr> removeSet = new HashSet<>();
		if (matchingTable != null) {
			for (Map<String, String> replaceMap : textToAdd) {
				Tr templateRow = findRowInTable(matchingTable, replaceMap.keySet());
				removeSet.add(templateRow);
				Tr row = copyElement(templateRow);
				for (Map.Entry<String, String> entry : replaceMap.entrySet()) {
					replaceParagraph(entry.getKey(), entry.getValue(), row);
				}
				matchingTable.getContent().add(row);
			}

			for (Tr tr : removeSet) {
				matchingTable.getContent().remove(tr);
			}
		}
	}

	public void replaceParagraph(String key, String value, Object element) {
		P paragraph = getParagraphWithText(element, key);
		if (paragraph == null) {
			return;
		}
		List<Text> texts = getAllElements(paragraph, new Text());
		texts.get(0).setValue(value);
		if (texts.size() == 1) {
			return;
		}
		for (int i = 1; i < texts.size(); i++) {
			removeChild(paragraph, texts.get(i));
		}
	}

	public void replaceTextRun(String key, String value, Object element) {
		P paragraph = getParagraphWithText(element, key);
		if (paragraph == null) {
			return;
		}
		List<Text> texts = getAllElements(paragraph, new Text());
		for (Text text : texts) {
			if (text.getValue().contains(key)) {
				String replacedTextValue = text.getValue().replace(key, value);
				text.setValue(replacedTextValue);
			}
		}
	}

	public void removeChild(Object parent, Object child) {
		if (parent instanceof ContentAccessor) {
			ContentAccessor contentAccessor = (ContentAccessor) parent;
			List<Object> children = contentAccessor.getContent();
			Set<Object> contentSet = new HashSet<>(children);
			removeChild(contentAccessor, contentSet, child);
		}
	}

	public void removeChild(ContentAccessor contentAccessor, Set<Object> contentSet, Object child) {
		if (contentSet.contains(child)) {
			contentAccessor.getContent().remove(child);
		} else if (child instanceof Child) {
			Child contentChild = (Child) child;
			Object parent = contentChild.getParent();
			if (parent != null) {
				removeChild(contentAccessor, contentSet, parent);
			}
		}
	}

	public Tr findRowInTable(Tbl table, Collection<String> keys) {
		return findRowInTable(table, keys.toArray(new String[0]));
	}

	public Tr findRowInTable(Tbl table, String ... keys) {
		List<Tr> rows = getAllElements(table, new Tr());
		for (Tr row : rows) {
			boolean hit = true;
			for (String key : keys) {
				if (getParagraphWithText(row, key) == null) {
					hit = false;
					break;
				}
			}
			if (hit) {
				return row;
			}
		}
		return null;
	}

	public P getParagraphWithText(Object element, String key) {
		List<P> paragraphs = getAllElements(element, new P());
		for (P paragraph : paragraphs) {
			List<Text> texts = getAllElements(paragraph, new Text());
			StringBuilder sb = new StringBuilder();
			texts.forEach(text -> sb.append(text.getValue()));
			if (sb.length() > 0 && sb.toString().contains(key)) {
				return paragraph;
			}
		}
		return null;
	}
}
