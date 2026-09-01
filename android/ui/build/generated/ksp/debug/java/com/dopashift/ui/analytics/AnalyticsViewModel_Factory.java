package com.dopashift.ui.analytics;

import com.dopashift.domain.repository.EfficiencyScoreRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class AnalyticsViewModel_Factory implements Factory<AnalyticsViewModel> {
  private final Provider<EfficiencyScoreRepository> efficiencyScoreRepositoryProvider;

  private AnalyticsViewModel_Factory(
      Provider<EfficiencyScoreRepository> efficiencyScoreRepositoryProvider) {
    this.efficiencyScoreRepositoryProvider = efficiencyScoreRepositoryProvider;
  }

  @Override
  public AnalyticsViewModel get() {
    return newInstance(efficiencyScoreRepositoryProvider.get());
  }

  public static AnalyticsViewModel_Factory create(
      Provider<EfficiencyScoreRepository> efficiencyScoreRepositoryProvider) {
    return new AnalyticsViewModel_Factory(efficiencyScoreRepositoryProvider);
  }

  public static AnalyticsViewModel newInstance(
      EfficiencyScoreRepository efficiencyScoreRepository) {
    return new AnalyticsViewModel(efficiencyScoreRepository);
  }
}
