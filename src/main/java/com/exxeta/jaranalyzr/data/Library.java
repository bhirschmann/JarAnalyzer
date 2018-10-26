package com.exxeta.jaranalyzr.data;

import java.io.File;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

public class Library {

	String name;
	String state;
	String kind;
	String parentName;
	public String getParentName() {
		return parentName;
	}

	public void setParentName(String parentName) {
		this.parentName = parentName;
	}

	SortedSet<Library> libsList;

	public String getState() {
		return state;
	}

	public Library setState(String state) {
		this.state = state;
		return this;
	}

	public String getKind() {
		return kind;
	}

	public Library setKind(String kind) {
		this.kind = kind;
		return this;
	}

	public SortedSet<Library> getLibsList() {
		return libsList;
	}

	public void setLibsList(SortedSet<Library> libsList) {
		this.libsList = libsList;
	}

	public void setName(String name) {
		this.name = name;
	}

	
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
		
		state = " ";
		kind = " ";
	}
	
	public Library addChild(String name) {
		if (libsList == null) {
			libsList = new TreeSet<Library>(Comparator.comparing(Library::getKind));
		}
		Library library = new Library(name);
		libsList.add(library);
		return library;
	}
	
	public Library addChild(Library lib) {
		if (libsList == null) {
			libsList = new TreeSet<Library>(Comparator.comparing(Library::getKind));
		}
		libsList.add(lib);
		return lib;
	}
	
	public SortedSet<Library> getLibs() {
		return libsList;
	}
	
	public String getName() {
		return name;
	}
	
	 @Override
	  public boolean equals(Object o) {
	    if (!(o instanceof Library)) {
	      return false;
	    }

	    Library l = (Library) o;
	    if (this.name == l.getName()) {
	      return true;
	    }

	    return false;
	  }

	  @Override
	  public int hashCode() {
	    return this.name.hashCode();
	  }

	  @Override
	  public String toString() {
	    return "(" + name + ", " + kind + ", " + state +")";
	  }	
}
