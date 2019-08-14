package de.kune.kache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes a method as cached. A cached method will return previously returned values without re-calculating them if<br/>
 * the method is a class method and has been called with equal parameters before on the same receiver<br/>
 * or the method is a static method and has been called with equal parameters before<br/>
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Cached {
}
