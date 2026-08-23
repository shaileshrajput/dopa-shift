package com.dopashift.data.di;

import com.dopashift.data.local.DopaShiftDatabase;
import com.dopashift.data.local.dao.InterceptionRuleDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
    "KotlinInternalInJava"
})
public final class DataModule_ProvideInterceptionRuleDaoFactory implements Factory<InterceptionRuleDao> {
  private final Provider<DopaShiftDatabase> dbProvider;

  public DataModule_ProvideInterceptionRuleDaoFactory(Provider<DopaShiftDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public InterceptionRuleDao get() {
    return provideInterceptionRuleDao(dbProvider.get());
  }

  public static DataModule_ProvideInterceptionRuleDaoFactory create(
      Provider<DopaShiftDatabase> dbProvider) {
    return new DataModule_ProvideInterceptionRuleDaoFactory(dbProvider);
  }

  public static InterceptionRuleDao provideInterceptionRuleDao(DopaShiftDatabase db) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideInterceptionRuleDao(db));
  }
}
