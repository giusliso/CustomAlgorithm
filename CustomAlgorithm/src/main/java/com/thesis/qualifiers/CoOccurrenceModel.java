package com.thesis.qualifiers;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

import org.grouplens.grapht.annotation.DefaultImplementation;
import com.thesis.models.CoOccurrenceMatrixModel;

@Documented
@Qualifier
@DefaultImplementation(CoOccurrenceMatrixModel.class)
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CoOccurrenceModel {
}