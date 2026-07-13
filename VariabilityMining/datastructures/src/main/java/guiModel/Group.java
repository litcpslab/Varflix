/***
 
    This Source Code Form is subject to the terms of the Mozilla
    Public License, v. 2.0. If a copy of the MPL was not distributed
    with this file, You can obtain one at
    https://mozilla.org/MPL/2.0/.*
    Contributors:
    Michael Schmidhammer
    
    Modifications: 
    Copyright (c) 2025 Johannes Kepler University Linz
  	LIT Cyber-Physical Systems Lab
 	Contributors:
 	Alexander Stummer - Added IDs for groups
**/

package guiModel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Group extends Difference {
	
	private int id;
    private String description;
    private Set<String> occurrences;
    private List<Element> elements;
    private ObservableList<String> previousNames = FXCollections.observableArrayList();

    public Group(int id, String name) {
    	this.id = id;
        this.name = new SimpleStringProperty(name);
        this.occurrences = new HashSet<>();
        this.elements = new ArrayList<>();
    }

    public Group(Group group) {
        this.id = group.getId();
        this.name = group.getName();
        this.occurrences = group.getOccurrences();
        this.elements = new ArrayList<>();
        this.elements.addAll(group.getElements());
        this.previousNames = group.getPreviousNames();
    }

    public Group() {

    }

    public int getId() {
		return id;
	}

    public Set<String> getOccurrences() {
        return occurrences;
    }

    public String getDescription() {
        return description;
    }

        public List<Element> getElements() {
        return elements;
    }

    public void addOccurrence(String occurrence) {
        this.occurrences.add(occurrence);
    }

    public void addElement(Element element) {
        this.elements.add(element);
    }

    public void addElementAt(Element element, int index) {
        this.elements.add(index, element);
    }

    public void setId(int id) {
		this.id = id;
	}

	public void setOccurrences(Set<String> occurrences) {
		this.occurrences = occurrences;
	}

	public void setElements(List<Element> elements) {
		this.elements = elements;
	}

    public ObservableList<String> getPreviousNames() {
        return previousNames;
    }

    public void addPreviousName(String previousName) {
        if (!previousNames.contains(previousName)) {
            previousNames.add(previousName);
        }
    }

    public void removePreviousName(String previousName) {
        previousNames.remove(previousName);
    }

    public void setName(StringProperty name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

	@Override
    public String toString() {
        return name.get();
    }
}
