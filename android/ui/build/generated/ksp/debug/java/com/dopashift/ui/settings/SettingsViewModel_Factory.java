package com.dopashift.ui.settings;

import com.dopashift.domain.port.QuietHoursProvider;
import com.dopashift.domain.repository.LlmConfigRepository;
import com.dopashift.domain.repository.UserPreferencesRepository;
import com.dopashift.domain.service.LlmProviderService;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<UserPreferencesRepository> userPreferencesRepositoryProvider;

  private final Provider<LlmConfigRepository> llmConfigRepositoryProvider;

  private final Provider<QuietHoursProvider> quietHoursProvider;

  private final Provider<LlmProviderService> llmProviderServiceProvider;

  private SettingsViewModel_Factory(
      Provider<UserPreferencesRepository> userPreferencesRepositoryProvider,
      Provider<LlmConfigRepository> llmConfigRepositoryProvider,
      Provider<QuietHoursProvider> quietHoursProvider,
      Provider<LlmProviderService> llmProviderServiceProvider) {
    this.userPreferencesRepositoryProvider = userPreferencesRepositoryProvider;
    this.llmConfigRepositoryProvider = llmConfigRepositoryProvider;
    this.quietHoursProvider = quietHoursProvider;
    this.llmProviderServiceProvider = llmProviderServiceProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(userPreferencesRepositoryProvider.get(), llmConfigRepositoryProvider.get(), quietHoursProvider.get(), llmProviderServiceProvider.get());
  }

  public static SettingsViewModel_Factory create(
      Provider<UserPreferencesRepository> userPreferencesRepositoryProvider,
      Provider<LlmConfigRepository> llmConfigRepositoryProvider,
      Provider<QuietHoursProvider> quietHoursProvider,
      Provider<LlmProviderService> llmProviderServiceProvider) {
    return new SettingsViewModel_Factory(userPreferencesRepositoryProvider, llmConfigRepositoryProvider, quietHoursProvider, llmProviderServiceProvider);
  }

  public static SettingsViewModel newInstance(UserPreferencesRepository userPreferencesRepository,
      LlmConfigRepository llmConfigRepository, QuietHoursProvider quietHoursProvider,
      LlmProviderService llmProviderService) {
    return new SettingsViewModel(userPreferencesRepository, llmConfigRepository, quietHoursProvider, llmProviderService);
  }
}
