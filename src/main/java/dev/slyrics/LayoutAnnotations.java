package dev.slyrics;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The annotations {@link ConfigLayout} uses to describe the OneConfig page: tabs, sections, options and their limits. */
public final class LayoutAnnotations {
	private LayoutAnnotations() {}

	/** On ConfigLayout itself: the top-level tabs, in order. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface Page {
		Class<?>[] categories();
	}

	/** A tab (with sections in {@code categories}) or a section of one. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.TYPE)
	public @interface Category {
		String value();

		Class<?>[] categories() default {};
	}

	/** A setting; {@code id} is the name of the field in {@link Config} that holds it. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface ConfigEntry {
		String id();
	}

	/** A button; the field is the Runnable it runs. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface ConfigButton {
		String title();

		String text();
	}

	/** The description shown under an option. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface Comment {
		String value();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface Range {
		double min();

		double max();
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface Slider {}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface Color {}
}
