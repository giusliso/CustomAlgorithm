/* This file may be freely modified, used, and redistributed without restriction. */
import org.grouplens.lenskit.ItemRecommender;
import org.grouplens.lenskit.ItemScorer;
import org.grouplens.lenskit.baseline.BaselineScorer
import org.grouplens.lenskit.baseline.ItemMeanRatingItemScorer
import org.grouplens.lenskit.baseline.UserMeanBaseline
import org.grouplens.lenskit.baseline.UserMeanItemScorer
import org.grouplens.lenskit.core.LenskitConfiguration;
import org.grouplens.lenskit.data.dao.EventDAO;
import org.grouplens.lenskit.eval.data.crossfold.RandomOrder
import org.grouplens.lenskit.eval.metrics.predict.NDCGPredictMetric
import org.grouplens.lenskit.eval.metrics.predict.RMSEPredictMetric
import org.grouplens.lenskit.knn.item.ItemItemScorer
import org.grouplens.lenskit.knn.item.ItemSimilarity;
import org.grouplens.lenskit.transform.normalize.BaselineSubtractingUserVectorNormalizer
import org.grouplens.lenskit.transform.normalize.UserVectorNormalizer
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity;
import org.grouplens.lenskit.vectors.similarity.VectorSimilarity;


trainTest("eval") {
    // options can be listed here in any order. This order happens to make sense to me.

    // this file will contain summary level results for each algorithm and each crossfold.
    output "build/eval-results.csv"
    // this file will contain metric results for every user. This is useful in careful statistical analysis.
    userOutput "build/eval-user.csv"

    // these line are optional, having them will enable disk based cacheing.
    // This means if you re-run an analysis without changing the configuration, lenskit won't have
    // to rebuild algorithm models, and can instead load the pre-built models from disk.
    componentCacheDirectory "build/componentCache"
    cacheAllComponents true

    // add metrics to the evaluation. To find the name of metrics in lenskit check out org.grouplens.lenskit.eval.metrics
    metric RMSEPredictMetric
    metric NDCGPredictMetric

    // add datasets to the evaluation.
    // strictly speaking, this adds all five crossfold splits as _seperate_ datasets.
    // this can be repeated if you want to evaluate on multiple datasets at the same time. Output
    // will contain a "dataset" row.
    dataset crossfold("ml-1m") {
        source csvfile {
            file "dataset/ml-1m/ratings.dat"
            delimiter "::"
            domain {
                minimum 1
                maximum 5
                precision 1
            }
            order RandomOrder    // choose test ratings randomly
            partitions 5         // make five partitions
            train 'build/crossfold/train.%d.pack'
            test 'build/crossfold/test.%d.pack'

        }
    }



    // Standard item-item CF.
    algorithm("ItemItem") {
        bind ItemScorer to ItemItemScorer
        bind UserVectorNormalizer to BaselineSubtractingUserVectorNormalizer
        within (UserVectorNormalizer) {
            bind (BaselineScorer, ItemScorer) to ItemMeanRatingItemScorer
        }
    }

    // Custom algorithm
    algorithm("Custom") {
        bind ItemRecommender to SeedRecommender
		bind ItemScorer to ItemItemScorer
    }

}
