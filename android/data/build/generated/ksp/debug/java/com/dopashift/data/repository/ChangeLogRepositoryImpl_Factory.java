package com.dopashift.data.repository;

import com.dopashift.data.local.dao.ChangeLogDao;
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
public final class ChangeLogRepositoryImpl_Factory implements Factory<ChangeLogRepositoryImpl> {
  private final Provider<ChangeLogDao> daoProvider;

  private ChangeLogRepositoryImpl_Factory(Provider<ChangeLogDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public ChangeLogRepositoryImpl get() {
    return newInstance(daoProvider.get());
  }

  public static ChangeLogRepositoryImpl_Factory create(Provider<ChangeLogDao> daoProvider) {
    return new ChangeLogRepositoryImpl_Factory(daoProvider);
  }

  public static ChangeLogRepositoryImpl newInstance(ChangeLogDao dao) {
    return new ChangeLogRepositoryImpl(dao);
  }
}
