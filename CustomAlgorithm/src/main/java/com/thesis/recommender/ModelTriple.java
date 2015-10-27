package com.thesis.recommender;

import org.grouplens.lenskit.knn.item.model.ItemItemModel;

public class ModelTriple {

	private double weight;
	private ItemItemModel model;

	public ModelTriple(ItemItemModel model, double weight) {

		this.model = model;
		this.weight = weight;
	}


	public double getWeight() {
		return weight;
	}

	public ItemItemModel getModel() {
		return model;
	}
}
