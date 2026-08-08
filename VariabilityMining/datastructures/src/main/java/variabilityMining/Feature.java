/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla
 * Public License, v. 2.0. If a copy of the MPL was not distributed
 * with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2025 Johannes Kepler University Linz
 * LIT Cyber-Physical Systems Lab
 * Contributors:
 *  Alexander Stummer - Initial Implementation
********************************************************************************/

package variabilityMining;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.annotations.Expose;

public class Feature implements Comparable<Feature> {

	@Expose
	private String name;
	
	private Feature parent;
	
	private List<Feature> children;
	
	private boolean orParent;
	
	private boolean alternativeParent;
	
	private boolean mandatory;

	private boolean isAbstract;
	
	public Feature(String name) {
		this.name = name;
	}
	
	public Feature(String name, Feature parent) {
		this.name = name;
		this.parent = parent;
		mandatory = false;
		isAbstract = false;
	}
	
	public String getName() {
		return name;
	}
	
	@Override
	public String toString() {
		return name;
	}
	
	public void setAlternativeParent(boolean isAlternativeParent) {
		this.alternativeParent = isAlternativeParent;
	}
	
	public void setOrParent(boolean isOrParent) {
		this.orParent = isOrParent;
	}
	
	public boolean isAlternativeParent() {
		return alternativeParent;
	}
	
	public boolean isOrParent() {
		return orParent;
	}
	
	public boolean isMandatory() {
		return mandatory;
	}
	
	public void setMandatory(boolean isMandatory) {
		this.mandatory = isMandatory;
	}

	public void setAbstract(boolean isAbstract) {
		this.isAbstract = isAbstract;
	}

	public boolean isAbstract() {
		return isAbstract;
	}

	public boolean isOptional(){
        return !mandatory&& !getParent().isAlternativeParent() && !getParent().isOrParent();
    }
	public void setChildren(List<Feature> children) {
		this.children = children;
	}
	
	public void setParent(Feature parent) {
		this.parent = parent;
	}
	
	public Feature getParent() {
		return parent;
	}
	
	public List<Feature> getChildren() {
		return children;
	}
	
	public void addChild(Feature child) {
		if(children == null) {
			children = new ArrayList<>();
		}
		children.add(child);
	}
	
	public void addChildren(List<Feature> childList) {
		if(children == null) {
			children = new ArrayList<>();
		}
		children.addAll(childList);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Feature other = (Feature) obj;
		return Objects.equals(name, other.name);
	}
	
	
	@Override
	public int compareTo(Feature other) {
		return this.name.compareTo(other.name);
	}
}
