package com.dopashift.interception.reminder;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class ReminderEvaluationWorker_AssistedFactory_Impl implements ReminderEvaluationWorker_AssistedFactory {
  private final ReminderEvaluationWorker_Factory delegateFactory;

  ReminderEvaluationWorker_AssistedFactory_Impl(ReminderEvaluationWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public ReminderEvaluationWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<ReminderEvaluationWorker_AssistedFactory> create(
      ReminderEvaluationWorker_Factory delegateFactory) {
    return InstanceFactory.create(new ReminderEvaluationWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<ReminderEvaluationWorker_AssistedFactory> createFactoryProvider(
      ReminderEvaluationWorker_Factory delegateFactory) {
    return InstanceFactory.create(new ReminderEvaluationWorker_AssistedFactory_Impl(delegateFactory));
  }
}
