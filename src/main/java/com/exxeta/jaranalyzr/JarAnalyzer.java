package com.exxeta.jaranalyzr;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.exxeta.jaranalyzr.data.Library;
import com.google.gson.Gson;

public class JarAnalyzer {

	final static String JAR_ARCHIVE = "jar";
	final static String WAR_ARCHIVE = "war";
	final static String EAR_ARCHIVE = "ear";
	
	final static String TH = "||";
	final static String TD = "|";
	final static String LF = "\n";

	private int counterJar = 0;
	private int counterWar = 0;
	private int counterEar = 0;
	
	Library rootLib;
	String internalToken;
	boolean checkForInternalArchives;
	
	final static String TEMP_SUBFOLDER = "unzipTemp";

	final static Map<String,Path> TEMP_FILES = new HashMap<String,Path>();
	
	public JarAnalyzer(String filename, String internalToken, boolean exportJson, boolean exportWikiMarkup) {
		
		this.internalToken = internalToken;
		
		if (internalToken!=null && internalToken.length()>0) {
			this.checkForInternalArchives = true;
			System.out.println("Checking for internal archives using '"+internalToken+"'");
		}
		Date startTime = new Date();
		
		rootLib = new Library(filename);
		Library lib = analyzeArchive(filename, rootLib);
		
		// add the kind of the root archive
		if (filename.endsWith(JAR_ARCHIVE)) {
			lib.setKind(JAR_ARCHIVE);
		} else if (filename.endsWith(WAR_ARCHIVE)) {
			lib.setKind(WAR_ARCHIVE);
		} else if (filename.endsWith(EAR_ARCHIVE)) {
			lib.setKind(EAR_ARCHIVE);
		} 
		
		Date endTime = new Date();
		long timeElapsed = endTime.getTime() - startTime.getTime();
		Date dateElapsed = new Date(timeElapsed);

		presentResult(lib, dateElapsed);
		
		if (exportJson) {
			writeJsonFile(lib, filename);
		}
		if (exportWikiMarkup) {
			writeWikiMarkup(lib, filename);
		}
		cleanup();
	}
	
	private Library analyzeArchive(String arcFilename, Library parentLib) {

		ZipInputStream zis = null;
		try {
			zis = new ZipInputStream(new FileInputStream(arcFilename));
			ZipEntry zipEntry = zis.getNextEntry();

			while(zipEntry != null){
				String fileName = zipEntry.getName();
				
				if (fileName.endsWith(JAR_ARCHIVE)) {
					addLib(arcFilename, JAR_ARCHIVE, fileName, parentLib);
				}
				else if (fileName.endsWith(WAR_ARCHIVE)) {
					
					// extract war for further analysis
					Path localFilename = extractFileFromArchive(arcFilename, fileName);
					Library library = new Library(fileName);
					library.setParentName(parentLib.getName());
					library.setKind(WAR_ARCHIVE);
					
					// analyze the extracted war and put its libs in the given library object
					// (build the tree recursively)
					analyzeArchive(localFilename.toString(), library);
					
					addLib(arcFilename, WAR_ARCHIVE, library, parentLib);
					cleanup(localFilename);
					counterWar++;
				}
				else if (fileName.endsWith(EAR_ARCHIVE)) {
					addLib(arcFilename, EAR_ARCHIVE, fileName, parentLib);
					counterEar++;
				}
				zipEntry = zis.getNextEntry();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
        try {
			zis.closeEntry();
			zis.close();
		} catch (IOException e) {
			// noop
		}
        return parentLib;
	}
	
	/** 
	 * Get the version of the achive, 
	 * either from the embedded MANIFEST.MF or from the filename.
	 * @param lib
	 * @return Version string
	 */
	private String getArchiveVersion(Library lib) {
		// "Implementation-Version"
		// Implementation-Version: 01.02.59-CS.0-7618
		return null;
	}

	private void addLib(String arcFilename, String kind, String fileName, Library parentLib) {
		Library lib = new Library(fileName);
		addLib(arcFilename, kind, lib, parentLib);
	}

	private void addLib(String arcFilename, String kind, Library lib, Library parentLib) {
		String state = "";
		if (this.checkForInternalArchives) {
			state = checkArchiveState(arcFilename, lib.getName());
			System.out.println(state);
		}
		lib.setKind(kind).setState(state).setParentName(parentLib.getName());
		parentLib.addChild(lib);
		counterJar++;
	}

	/**
	 * Analyze the given archive for the embedded archive with the given name, 
	 * and check if it has the given search token in its path somewhere, 
	 * indicating that it is an internal archive. 
	 * (i.e. it has classes in it with the Java package "com.mycompany")
	 * @param archiveName
	 * @param nameOfFileToExtract
	 * @return
	 */
	private String checkArchiveState(String archiveName, String nameOfFileToExtract) {

		System.out.print("Checking "+nameOfFileToExtract);
		String state = "external";
        ZipInputStream zis = null;
        ZipInputStream zis2 = null;
		try {
			zis = new ZipInputStream(new FileInputStream(archiveName));
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
        ZipEntry zipEntry;
		try {
			zipEntry = zis.getNextEntry();
			System.out.print("...");
			while (zipEntry != null) {
				String fileName = zipEntry.getName();
				
				// is this the file in the archive to check?
				if (fileName!=null && fileName.endsWith(nameOfFileToExtract)) {
					zis2 = new ZipInputStream(zis);
					ZipEntry zipEntry2;
					zipEntry2 = zis2.getNextEntry();
					while (zipEntry2 != null) {
						String fileName2 = zipEntry2.getName();
						if (fileName2.contains(internalToken)) {
							state = "internal";
							return state;
						}
						zipEntry2 = zis2.getNextEntry();
					}
				}
				zipEntry = zis.getNextEntry();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally {
			try {
				zis.closeEntry();
				zis.close();
				zis2.closeEntry();
				zis2.close();
			} catch (Exception e) {
				// noop
			}
		}
        return state;
	}

	/**
	 * Extract the given file from archive and extract it to the user's temp directory.
	 * @param archiveName
	 * @param nameOfFileToExtract
	 * @return the path to the extracted file in the temp dir
	 */
	private Path extractFileFromArchive(String archiveName, String nameOfFileToExtract) {

		Path tempFile = null;
		byte[] buffer = new byte[1024];
        ZipInputStream zis = null;
		try {
			zis = new ZipInputStream(new FileInputStream(archiveName));
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
        ZipEntry zipEntry;
		try {
			zipEntry = zis.getNextEntry();
			Path tempDirectory = Files.createTempDirectory(TEMP_SUBFOLDER);
			TEMP_FILES.put("tempdir", tempDirectory);
			while (zipEntry != null) {
				String fileName = zipEntry.getName();

				// check if current zip entry is the wanted file to extract
				if (fileName.equals(nameOfFileToExtract)) {
					
					// extract the file
					String tempFileName = fileName.replaceAll("/", "_");
					System.out.println("Creating temp file: " + tempFileName);
					try {
						tempFile = Files.createTempFile(tempDirectory, null, tempFileName);
					} 
					catch (IllegalArgumentException iae) {
						System.err.println("Error creating temp file: " + tempFileName);
						System.err.println(iae.getMessage());
					}
					FileOutputStream fos = new FileOutputStream(tempFile.toFile());
					int len;
					while ((len = zis.read(buffer)) > 0) {
						fos.write(buffer, 0, len);
					}
					fos.close();
					break;
				}
				zipEntry = zis.getNextEntry();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally {
			try {
				zis.closeEntry();
				zis.close();
			} catch (IOException e) {
				// noop
			}
		}
        
        return tempFile;
	}

	private void cleanup(Path filename) {
		// delete the temp file
		try {
			Files.deleteIfExists(filename);
		} catch (IOException e) {
			System.err.println("ERROR: Unable to delete temp file");
			e.printStackTrace();
		}		
	}

	/**
	 * Print the summary of the result to the console
	 * @param library
	 * @param timeElapsed
	 */
	private void presentResult(Library library, Date timeElapsed) {

//        for (Iterator<Library> iterator = library.getLibs().iterator(); iterator.hasNext();) {
//			Library lib = iterator.next();
//			System.out.println(lib.getName());
//		}
        
		System.out.println("-------------------------------------");
		System.out.println("Contained archive files in archive:");
		System.out.println(counterEar + " ear files");
		System.out.println(counterWar + " war files");
		System.out.println(counterJar + " jar files");
		System.out.println("-------------------------------------");
		System.out.println("Analysis took " + timeElapsed.getMinutes() + "min " + timeElapsed.getSeconds()+"sec");
		System.out.println();
		
		System.out.println(library.getName());
	}
	
	/**
	 * Export the result in a structure to a Json file
	 * @param library
	 * @param filename
	 */
	private void writeJsonFile(Library library, String filename) {
		Gson gson = new Gson();
		String json = gson.toJson(library);

		BufferedWriter writer= null;
		try {
			writer = new BufferedWriter(new FileWriter(filename + ".json"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	    try {
			writer.write(json);
			writer.close();	
			System.out.println("JSON file written to: " + filename + ".json");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/** To handle the formatting for viewing the nested archives */
	private int nestingLevel = 0;
	private int maxNestingLevel = 0;
	
	/**
	 * Export the result in a table in Wiki markup format, 
	 * for easy import in a Wiki like Confluence.
	 * @param lib
	 * @param filename
	 */
	private void writeWikiMarkup(Library lib, String filename) {

		determineMaxNestingLevel(lib);
		nestingLevel = 0;
		
		BufferedWriter writer= null;
		try {
			writer = new BufferedWriter(new FileWriter(filename + ".markup"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	    try {
    	
	    	// write table body
	    	StringWriter writerBody = new StringWriter();
	    	writeWikiMarkupOfNestedLibs(lib, writerBody);

	    	// write table header
	    	writer.write(TH + lib.getState() + TH + lib.getKind() + TH + lib.getName()  + TH );
			for (int i=1; i<maxNestingLevel; i++) {
				// add extra columns for the nested archives
				writer.write(" " + TH);
			}
	    	writer.write(LF);
	    	
	    	// now append the body
	    	writer.write(writerBody.getBuffer().toString());
			writer.close();	
			System.out.println("markup file written to: " + filename + ".markup");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void writeWikiMarkupOfNestedLibs(Library lib, Writer writer)
			throws IOException {

		nestingLevel++;
		SortedSet<Library> libList = lib.getLibsList();
		for (Iterator<Library> iterator = libList.iterator(); iterator.hasNext();) {
			Library library = iterator.next();
			writer.write(TD + library.getState() + TD + library.getKind());
			for (int i=1; i<nestingLevel; i++) {
				// indent the nested archived to the next column
				writer.write( TD + " ");
			}
			writer.write( TD + library.getName() + TD);
			for (int i=0; i<maxNestingLevel - nestingLevel; i++) {
				// fill the space of empty cells to the last column
				writer.write( " " + TD);
			}
			writer.write( LF );
			
			// check if nested libs exist
			SortedSet<Library> libs = library.getLibs();
			if (libs != null && !libs.isEmpty()) {
				writeWikiMarkupOfNestedLibs(library, writer);
				nestingLevel--;
			}
			
		}
	}

	private void determineMaxNestingLevel(Library lib) {

		nestingLevel++;
		if (nestingLevel > maxNestingLevel) maxNestingLevel = nestingLevel;
		
		SortedSet<Library> libList = lib.getLibsList();
		for (Iterator<Library> iterator = libList.iterator(); iterator.hasNext();) {
			Library library = iterator.next();
			// check if nested libs exist
			SortedSet<Library> libs = library.getLibs();
			if (libs != null && !libs.isEmpty()) {
				determineMaxNestingLevel(library);
				nestingLevel--;
			}
		}
	}

	private void cleanup() {

		// delete the temp files and folders
		Set<String> keySet = TEMP_FILES.keySet();
		Iterator<String> iterator = keySet.iterator();
		Path path;
		while (iterator.hasNext()) {
			path = TEMP_FILES.get(iterator.next());
			try {
				Files.delete(path);
			} catch (IOException e) {
				System.err.println("Can't delete the temp folder:");
				System.err.println(path.toString());
				e.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		
		String internalToken = null;
		if (args.length < 1) {
			System.err.println("Please provide the required parameters");
			System.err.println("1. parameter: <Java archive to analyze>");
			System.err.println("additional optional parameters: ");
			System.err.println("-i <search token> (The search token to search for in nested archives, indication it is an internal archive, i.e. a Java package which name indicates its internal existence )");
			System.err.println("-j (Export result as tree in a Json file)");
			System.err.println("-m (Export result as table in a Wiki markup file, for easy import in a Wiki)");
			System.err.println();
			System.err.println("Example arguments: /users/bob/application.ear -i mycompany");
			System.exit(0);
		}
		
		boolean exportJson = false;
		boolean exportWikiMarkup = false;
		
		if (args.length > 1) {
			List<String> argsList = Arrays.asList(args);
			Iterator<String> iterator = argsList.iterator();
			while (iterator.hasNext()) {
				String arg = iterator.next();
				if ("-i".equals(arg) && iterator.hasNext()) {
					internalToken = iterator.next();
				}
				else if ("-j".equals(arg)) {
					exportJson = true;
				}
				else if ("-m".equals(arg)) {
					exportWikiMarkup = true;
				}
			}
		}
		new JarAnalyzer(args[0], internalToken, exportJson, exportWikiMarkup);
	}
}
