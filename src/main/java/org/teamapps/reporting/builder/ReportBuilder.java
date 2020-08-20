package org.teamapps.reporting.builder;

import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.teamapps.reporting.convert.DocumentConverter;
import org.teamapps.reporting.convert.DocumentFormat;
import org.teamapps.reporting.convert.UnsupportedFormatException;

import javax.xml.bind.JAXBException;
import java.io.*;
import java.util.*;

public class ReportBuilder {

	public static ReportBuilder create(DocumentFormat inputFormat, File templateFile) throws FileNotFoundException {
		return create(inputFormat, new BufferedInputStream(new FileInputStream(templateFile)));
	}

	public static ReportBuilder create(DocumentFormat inputFormat, InputStream inputStream) {
		return new ReportBuilder(inputFormat, inputStream);
	}

	private final DocumentFormat inputFormat;
	private final InputStream inputStream;
	private File outputFile;
	private Map<String, String> replacementMap = new HashMap();
	private List<TableBuilder> tableBuilders = new ArrayList<>();

	public ReportBuilder(DocumentFormat inputFormat, InputStream inputStream) {
		this.inputFormat = inputFormat;
		this.inputStream = inputStream;
	}

	public void setOutputFile(File outputFile) {
		this.outputFile = outputFile;
	}

	public ReportBuilder addReplacement(String key, String value) {
		replacementMap.put(key, value);
		return this;
	}

	public TableBuilder createTableBuilder(String ... keys){
		return createTableBuilder(Arrays.asList(keys));
	}

	public TableBuilder createTableBuilder(List<String> keys){
		TableBuilder tableBuilder = new TableBuilder(keys);
		tableBuilders.add(tableBuilder);
		return tableBuilder;
	}

	public File build() throws Exception {
		if (inputFormat != DocumentFormat.DOCX) {
			UnsupportedFormatException.throwException(inputFormat);
		}
		WordprocessingMLPackage template = DocumentTemplateLoader.getTemplate(inputStream);
		processDocument(template);
		File outputFile = createOutputFile(DocumentFormat.DOCX);
		template.save(outputFile);
		return outputFile;
	}

	public File build(DocumentFormat outputFormat, DocumentConverter converter) throws Exception {
		InputStream documentInputStream = inputStream;
		if (inputFormat != DocumentFormat.DOCX) {
			File tempFile = File.createTempFile("temp", "." + DocumentFormat.DOCX.getFormat());
			converter.convertDocument(inputStream, inputFormat, tempFile, DocumentFormat.DOCX);
			documentInputStream = new BufferedInputStream(new FileInputStream(tempFile));
		}

		WordprocessingMLPackage template = DocumentTemplateLoader.getTemplate(documentInputStream);
		processDocument(template);

		File outputFile = createOutputFile(outputFormat);
		if (outputFormat == DocumentFormat.DOCX) {
			template.save(outputFile);
			return outputFile;
		} else {
			converter.convertDocument(template, outputFile, outputFormat);
			return outputFile;
		}
	}

	private void processDocument(WordprocessingMLPackage template) throws Docx4JException, JAXBException {
		MainDocumentPart mainDocumentPart = template.getMainDocumentPart();
		DocumentBuilder documentBuilder = new DocumentBuilder();
		for (Map.Entry<String, String> entry : replacementMap.entrySet()) {
			documentBuilder.replaceTextRun(entry.getKey(), entry.getValue(), mainDocumentPart);
		}

		for (TableBuilder tableBuilder : tableBuilders) {
			documentBuilder.fillTable(tableBuilder.createReplacementMap(), template, tableBuilder.getKeys());
		}
	}

	private File createOutputFile(DocumentFormat format) throws IOException {
		if (outputFile != null) {
			return outputFile;
		} else {
			return File.createTempFile("temp", "." + format.getFormat());
		}
	}

}