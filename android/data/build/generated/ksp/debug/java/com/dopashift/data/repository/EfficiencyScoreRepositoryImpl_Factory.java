package com.dopashift.data.repository;

import com.dopashift.data.local.dao.EfficiencyScoreDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class EfficiencyScoreRepositoryImpl_Factory implements Factory<EfficiencyScoreRepositoryImpl> {
  private final Provider<EfficiencyScoreDao> daoProvider;

  private EfficiencyScoreRepositoryImpl_Factory(Provider<EfficiencyScoreDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public EfficiencyScoreRepositoryImpl get() {
    return newInstance(daoProvider.get());
  }

  public static EfficiencyScoreRepositoryImpl_Factory create(
      Provider<EfficiencyScoreDao> daoProvider) {
    return new EfficiencyScoreRepositoryImpl_Factory(daoProvider);
  }

  public static EfficiencyScoreRepositoryImpl newInstance(EfficiencyScoreDao dao) {
    return new EfficiencyScoreRepositoryImpl(dao);
  }
}
