package org.batfish.datamodel.matchers;

import static org.hamcrest.Matchers.equalTo;

import javax.annotation.Nonnull;
import org.batfish.datamodel.eigrp.EigrpInterfaceSettings;
import org.batfish.datamodel.matchers.EigrpInterfaceSettingsMatchersImpl.HasAsn;
import org.batfish.datamodel.matchers.EigrpInterfaceSettingsMatchersImpl.HasDelay;
import org.batfish.datamodel.matchers.EigrpInterfaceSettingsMatchersImpl.HasEnabled;
import org.hamcrest.Matcher;

public class EigrpInterfaceSettingsMatchers {

  private EigrpInterfaceSettingsMatchers() {}

  /**
   * Provides a matcher that matches if the {@link EigrpInterfaceSettings}'s asn is {@code
   * expectedAsn}.
   */
  public static @Nonnull Matcher<EigrpInterfaceSettings> hasAsn(long expectedAsn) {
    return new HasAsn(equalTo(expectedAsn));
  }

  /**
   * Provides a matcher that matches if the provided {@code subMatcher} matches the {@link
   * EigrpInterfaceSettings}'s asn.
   */
  public static @Nonnull Matcher<EigrpInterfaceSettings> hasAsn(
      @Nonnull Matcher<? super Long> subMatcher) {
    return new HasAsn(subMatcher);
  }

  /**
   * Provides a matcher that matches if the {@link EigrpInterfaceSettings}'s delay is {@code
   * expectedDelay}.
   */
  public static @Nonnull Matcher<EigrpInterfaceSettings> hasDelay(double expectedDelay) {
    return new HasDelay(equalTo(expectedDelay));
  }

  /**
   * Provides a matcher that matches if the provided {@code subMatcher} matches the {@link
   * EigrpInterfaceSettings}'s delay.
   */
  public static @Nonnull Matcher<EigrpInterfaceSettings> hasDelay(
      @Nonnull Matcher<? super Double> subMatcher) {
    return new HasDelay(subMatcher);
  }

  /**
   * Provides a matcher that matches if the {@link EigrpInterfaceSettings}'s enabled is {@code
   * expectedEnabled}.
   */
  public static @Nonnull Matcher<EigrpInterfaceSettings> hasEnabled(boolean expectedEnabled) {
    return new HasEnabled(equalTo(expectedEnabled));
  }

  /**
   * Provides a matcher that matches if the provided {@code subMatcher} matches the {@link
   * EigrpInterfaceSettings}'s enabled.
   */
  public static @Nonnull Matcher<EigrpInterfaceSettings> hasEnabled(
      @Nonnull Matcher<? super Boolean> subMatcher) {
    return new HasEnabled(subMatcher);
  }
}