package com.dopashift.data.di;

import com.dopashift.data.local.DopaShiftDatabase;
import com.dopashift.data.local.dao.EfficiencyScoreDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DataModule_ProvideEfficiencyScoreDaoFactory implements Factory<EfficiencyScoreDao> {
  private final Provider<DopaShiftDatabase> dbProvider;

  private DataModule_ProvideEfficiencyScoreDaoFactory(Provider<DopaShiftDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public EfficiencyScoreDao get() {
    return provideEfficiencyScoreDao(dbProvider.get());
  }

  public static DataModule_ProvideEfficiencyScoreDaoFactory create(
      Provider<DopaShiftDatabase> dbProvider) {
    return new DataModule_ProvideEfficiencyScoreDaoFactory(dbProvider);
  }

  public static EfficiencyScoreDao provideEfficiencyScoreDao(DopaShiftDatabase db) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideEfficiencyScoreDao(db));
  }
}
