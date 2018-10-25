package com.exxeta.jaranalyzr.data;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class Library {

	String name;
	String state;
	String kind;
	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getKind() {
		return kind;
	}

	public Library setKind(String kind) {
		this.kind = kind;
		return this;
	}

	public List<Library> getLibsList() {
		return libsList;
	}

	public void setLibsList(List<Library> libsList) {
		this.libsList = libsList;
	}

	public void setName(String name) {
		this.name = name;
	}

	List<Library> libsList;
	
	public Library(String name) {
		
		// use only the filename, cut off the path
		int lastPathSeparator = name.lastIndexOf(File.separator);
		if (lastPathSeparator > 0) {
			name = name.substring(lastPathSeparator+1, name.length());
		}
		// remove also the slashes in the paths, which come from the archives paths
		int lastSlash = name.lastIndexOf("/");
		if (lastSlash > 0) {
			name = name.substring(lastSlash+1, name.length());
		}
		this.name = name;
	}
	
	public Library addChild(String name) {
		if (libsList == null) {
			libsList = new LinkedList<Library>();
		}
		Library library = new Library(name);
		libsList.add(library);
		return library;
	}
	
	public Library addChild(Library lib) {
		if (libsList == null) {
			libsList = new LinkedList<Library>();
		}
		libsList.add(lib);
		return lib;
	}
	
	public List<Library> getLibs() {
		return libsList;
	}
	
	public String getName() {
		return name;
	}
}
