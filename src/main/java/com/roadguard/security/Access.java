package com.roadguard.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares who may call an API endpoint. Put it on a controller class (default for all its methods)
 * and/or on individual methods (overrides the class). AccessInterceptor enforces it on the server
 * before the controller runs. An /api endpoint with NO annotation is refused (fail closed).
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Access {
    AccessLevel value();
}
