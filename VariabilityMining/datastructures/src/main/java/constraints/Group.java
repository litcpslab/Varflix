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

package constraints;

import java.util.List;
import java.util.Objects;

import com.google.gson.annotations.Expose;

import variabilityMining.Feature;

public class Group implements Constraint {

	@Expose
	protected List<Feature> features;
	
	@Expose
	protected Feature parentFeature;
	
	@Expose
	protected String type;
	
	public Group(List<Feature> features, Feature parentFeature, String type) {
		this.features = features;
		this.parentFeature = parentFeature;
		this.type = type;
	}
	
	public void addFeature(Feature feature) {
		features.add(feature);
	}
	
	public List<Feature> getFeatures() {
		return features;
	}

	public Feature getParent() {
		return parentFeature;
	}

    public void setParent(Feature parentFeature) {
        this.parentFeature = parentFeature;
    }
	
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}

	public void removeFeature(Feature feature) {
		features.remove(feature);
	}

	@Override
	public int hashCode() {
		return Objects.hash(features, parentFeature, type);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Group other = (Group) obj;
		return Objects.equals(features, other.features) && Objects.equals(parentFeature, other.parentFeature)
				&& Objects.equals(type, other.type);
	}
}
