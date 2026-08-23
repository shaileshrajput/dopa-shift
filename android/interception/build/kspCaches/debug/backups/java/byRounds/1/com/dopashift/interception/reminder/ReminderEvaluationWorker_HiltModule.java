package com.dopashift.interception.reminder;

import androidx.hilt.work.WorkerAssistedFactory;
import androidx.work.ListenableWorker;
import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import javax.annotation.processing.Generated;

@Generated("androidx.hilt.AndroidXHiltProcessor")
@Module
@InstallIn(SingletonComponent.class)
@OriginatingElement(
    topLevelClass = ReminderEvaluationWorker.class
)
public interface ReminderEvaluationWorker_HiltModule {
  @Binds
  @IntoMap
  @StringKey("com.dopashift.interception.reminder.ReminderEvaluationWorker")
  WorkerAssistedFactory<? extends ListenableWorker> bind(
      ReminderEvaluationWorker_AssistedFactory factory);
}
