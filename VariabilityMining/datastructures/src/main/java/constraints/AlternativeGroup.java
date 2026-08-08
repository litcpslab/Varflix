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

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import variabilityMining.Feature;

public class AlternativeGroup extends Group {
	
	public AlternativeGroup(List<Feature> features, Feature parent) { 
		super(features, parent, "Alternative");
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		
		Iterator<Feature> featureIterator = features.iterator();
		
		while(featureIterator.hasNext()) {
			Feature current = featureIterator.next();
			builder.append(current);
			
			if(featureIterator.hasNext()) {
				builder.append(" ⊕ ");
			}
		}
		
		builder.append(" (Parent: " + parentFeature + ")");
		
		return builder.toString();
	}

    @Override
    public int hashCode() {
        return java.util.Objects.hash(parentFeature != null ? parentFeature.getName() : null, getType());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        AlternativeGroup that = (AlternativeGroup) other;

        String thisParentName = this.parentFeature != null ? this.parentFeature.getName() : "";
        String thatParentName = that.parentFeature != null ? that.parentFeature.getName() : "";
        if (!thisParentName.equals(thatParentName)) return false;

        if (!java.util.Objects.equals(this.getType(), that.getType())) return false;
        if (this.features.size() != that.features.size()) return false;

        List<String> thisNames = this.features.stream().map(Feature::getName).sorted().collect(Collectors.toList());
        List<String> thatNames = that.features.stream().map(Feature::getName).sorted().collect(Collectors.toList());
        return thisNames.equals(thatNames);
    }
}
