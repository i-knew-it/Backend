package com.example.iknewit.common;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.hibernate.annotations.IdGeneratorType;

// PK 필드에 붙이면 INSERT 시 ULID(26자)를 자동 생성
@IdGeneratorType(UlidGenerator.class)
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface Ulid {
}
