package uk.ac.sdss.mdcheck;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
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
 * local rules expressed as XSL stylesheets.  At some point this may be extended
 * to include more complex checks that bring in information from the members file.
 * 
 * The current code is more general than it needs to be, so that that forward
 * evolution will be simpler.
 * 
 * @author iay
 *
 */
public class MetadataCheck implements Runnable {
	
	private final DocumentBuilderFactory dbFactory;
	private final String inputFileName;
	private final Document inputDoc;
	
	private final TransformerFactory tFactory;
	
	private class Checker {
		/** File object representing the stylesheet */
		private final File file;
		
		/** System identifier corresponding to the file. */
		private final String systemID;
		
		/** DOM tree form of the stylesheet */
		private final Document checkDoc;
		
		/**
		 * Get a simple name for this stylesheet for use in error messages.
		 * 
		 * @return a simple name for the Checker
		 */
		private String getName() {
			return this.file.getName();
		}

		/**
		 * Generates a JAXP Source for this stylesheet.  The Source's
		 * system ID is set to allow resolution of relative URIs within the
		 * transform.
		 * 
		 * @return a JAXP Source object.
		 */
		private Source getSource() {
			return new DOMSource(this.checkDoc, this.systemID);
		}

		Checker(String checkFileName) throws SAXException, IOException, ParserConfigurationException {
			this.file = new File(checkFileName);
			this.checkDoc = buildDocument(checkFileName);
			this.systemID = this.file.toURI().toString();
		}
	}
	
	private List<Checker> checkers = new ArrayList<Checker>();
	
	/**
	 * Global count of errors across all checks.
	 */
	private int errors = 0;

	private static void croak(String s, Exception e) {
		e.printStackTrace();
		System.err.println("Internal error: " + s + ": " + e.getMessage());
		System.exit(1);
	}

	private class MyErrorListener implements ErrorListener {
		
		private final String inputFileName;
		private final String checkerName;
		private boolean first = true;

		private void recordError(TransformerException e)
		{
			String msg = e.getMessage();
			if (first) {
				first = false;
				System.err.println("*** checking " + inputFileName +
						" with " + checkerName);
			}
			System.err.println(msg);
			if (msg.startsWith("[ERROR]")) errors++;
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
		
		MyErrorListener(String inputFileName, String checkerName) {
			this.inputFileName = new File(inputFileName).getName(); // last component of name only
			this.checkerName = checkerName;
		}
	}
	
	private Document buildDocument(String fileName)
		throws SAXException, IOException, ParserConfigurationException
	{
		DocumentBuilder db = dbFactory.newDocumentBuilder();
		return db.parse(fileName);
	}
	
	private void performCheck(Document input, String inputFileName, Checker checker)
		throws TransformerException
	{
		Transformer t = tFactory.newTransformer(checker.getSource());
		t.setErrorListener(new MyErrorListener(inputFileName, checker.getName()));
		t.transform(new DOMSource(input), new DOMResult());
	}
	
	public void run() {
		try {
			for (Checker check: checkers) {
				performCheck(inputDoc, inputFileName, check);
			}
		} catch (TransformerConfigurationException e) {
			croak("transformer configuration exception", e);
		} catch (TransformerException e) {
			croak("transformer exception", e);
		}
		if (errors != 0) {
			System.err.println("*** ERRORS ENCOUNTERED IN " +
					inputFileName + " ***");
			System.exit(1);
		}
	}

	private void addChecker(String checkFileName) throws SAXException, IOException, ParserConfigurationException {
		checkers.add(new Checker(checkFileName));
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
	private MetadataCheck(String inputFileName)
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
		if (args.length < 2) {
			System.err.println("Usage: MetadataCheck infile checkfile...");
			System.exit(1);
		}
		
		MetadataCheck mdc;
		try {
			mdc = new MetadataCheck(args[0]);
			for (int i = 1; i < args.length; i++) {
				mdc.addChecker(args[i]);
			}
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
