package com.dopashift.interception.reminder;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
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
public final class ReminderEvaluationWorker_Factory {
  private final Provider<ClientReminderEngine> clientReminderEngineProvider;

  public ReminderEvaluationWorker_Factory(
      Provider<ClientReminderEngine> clientReminderEngineProvider) {
    this.clientReminderEngineProvider = clientReminderEngineProvider;
  }

  public ReminderEvaluationWorker get(Context context, WorkerParameters workerParams) {
    return newInstance(context, workerParams, clientReminderEngineProvider.get());
  }

  public static ReminderEvaluationWorker_Factory create(
      Provider<ClientReminderEngine> clientReminderEngineProvider) {
    return new ReminderEvaluationWorker_Factory(clientReminderEngineProvider);
  }

  public static ReminderEvaluationWorker newInstance(Context context, WorkerParameters workerParams,
      ClientReminderEngine clientReminderEngine) {
    return new ReminderEvaluationWorker(context, workerParams, clientReminderEngine);
  }
}
