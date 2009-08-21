package uk.ac.sdss.mdcheck;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * MetadataCheck
 * 
 * This application checks an input file containing SAML metadata against a set of
 * local rules expressed as an XSL stylesheet.  At some point this will be extended
 * to allow multiple rule files to be checked in sequence, and include more complex
 * checks that bring in information from the members file.  The current code is
 * more general than it needs to be, so that that forward evolution will be simpler.
 * 
 * @author iay
 *
 */
public class MetadataCheck implements Runnable {
	
	private final DocumentBuilderFactory dbFactory;
	private final String inputFileName;
	private final Document inputDoc;
	private final String checkFileName;
	private final Document checkDoc;
	
	private final TransformerFactory tFactory;
	
	/**
	 * Global count of fatal errors across all checks.
	 */
	private int fatals = 0;

	private static void croak(String s, Exception e) {
		e.printStackTrace();
		System.err.println("Internal error: " + s + ": " + e.getMessage());
		System.exit(1);
	}

	private class MyErrorListener implements ErrorListener {
		
		private final String inputFileName;
		private final String checkFileName;
		private boolean first = true;

		private void recordError(TransformerException e)
		{
			String msg = e.getMessage();
			if (first) {
				first = false;
				System.err.println("*** checking " + inputFileName +
						" with " + checkFileName);
			}
			System.err.println(msg);
			if (msg.startsWith("***")) fatals++;
		}
		
		public void error(TransformerException exception)
				throws TransformerException {
			recordError(exception);
		}

		public void fatalError(TransformerException exception)
				throws TransformerException {
			recordError(exception);
		}

		public void warning(TransformerException exception)
				throws TransformerException {
			recordError(exception);
		}
		
		MyErrorListener(String inputFileName, String checkFileName) {
			this.inputFileName = inputFileName;
			this.checkFileName = checkFileName;
		}
	}
	
	private Document buildDocument(String fileName)
		throws SAXException, IOException, ParserConfigurationException
	{
		DocumentBuilder db = dbFactory.newDocumentBuilder();
		return db.parse(fileName);
	}
	
	private void performCheck(Document input, String inputFileName, Document check, String checkFileName)
		throws TransformerException
	{
		Transformer t = tFactory.newTransformer(new DOMSource(check));
		t.setErrorListener(new MyErrorListener(inputFileName, checkFileName));
		t.transform(new DOMSource(input), new DOMResult());
	}
	
	public void run() {
		try {
			performCheck(inputDoc, inputFileName, checkDoc, checkFileName);
		} catch (TransformerConfigurationException e) {
			croak("transformer configuration exception", e);
		} catch (TransformerException e) {
			croak("transformer exception", e);
		}
		if (fatals != 0) {
			System.err.println("*** FATAL ERRORS ENCOUNTERED IN " +
					inputFileName + " ***");
			System.exit(1);
		}
	}

	/**
	 * Constructor.
	 * 
	 * @param inputFileName name of the file containing the metadata
	 * 						to be checked
	 * @param checkFileName name of the file containing the stylesheet
	 * 						that will perform the checking
	 * @throws ParserConfigurationException 
	 * @throws IOException 
	 * @throws SAXException 
	 */
	private MetadataCheck(String inputFileName, String checkFileName)
		throws SAXException, IOException, ParserConfigurationException
	{
		/*
		 * Create a transformer factory.
		 */
		tFactory = TransformerFactory.newInstance();
		
		/*
		 * Create a document builder factory.
		 */
		dbFactory = DocumentBuilderFactory.newInstance();
		dbFactory.setNamespaceAware(true);
		// dbFactory.setValidating(true);
		
		/*
		 * Build documents for each of the input files.
		 */
		this.inputFileName = inputFileName;
		inputDoc = buildDocument(inputFileName);
		this.checkFileName = checkFileName;
		checkDoc = buildDocument(checkFileName);
	}
	
	/**
	 * Command-line entry point.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		/*
		 * Parse command-line arguments.
		 */
		if (args.length != 2) {
			System.err.println("Usage: MetadataCheck infile checkfile");
			System.exit(1);
		}
		
		MetadataCheck mdc;
		try {
			mdc = new MetadataCheck(args[0], args[1]);
			mdc.run();
		} catch (SAXException e) {
			croak("SAX exception creating MetadataCheck", e);
		} catch (IOException e) {
			croak("I/O exception creating MetadataCheck", e);
		} catch (ParserConfigurationException e) {
			croak("parser configuration exception creating MetadataCheck", e);
		}
	}

}
