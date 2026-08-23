package com.dopashift.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.dopashift.data.local.dao.ChangeLogDao;
import com.dopashift.data.local.dao.ChangeLogDao_Impl;
import com.dopashift.data.local.dao.DailyTodoDao;
import com.dopashift.data.local.dao.DailyTodoDao_Impl;
import com.dopashift.data.local.dao.EfficiencyScoreDao;
import com.dopashift.data.local.dao.EfficiencyScoreDao_Impl;
import com.dopashift.data.local.dao.GoalChecklistDao;
import com.dopashift.data.local.dao.GoalChecklistDao_Impl;
import com.dopashift.data.local.dao.GoalDao;
import com.dopashift.data.local.dao.GoalDao_Impl;
import com.dopashift.data.local.dao.HabitTrackDao;
import com.dopashift.data.local.dao.HabitTrackDao_Impl;
import com.dopashift.data.local.dao.InterceptionRuleDao;
import com.dopashift.data.local.dao.InterceptionRuleDao_Impl;
import com.dopashift.data.local.dao.ReminderDao;
import com.dopashift.data.local.dao.ReminderDao_Impl;
import com.dopashift.data.local.dao.SyncStateDao;
import com.dopashift.data.local.dao.SyncStateDao_Impl;
import com.dopashift.data.local.dao.TelemetryDao;
import com.dopashift.data.local.dao.TelemetryDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class DopaShiftDatabase_Impl extends DopaShiftDatabase {
  private volatile GoalDao _goalDao;

  private volatile DailyTodoDao _dailyTodoDao;

  private volatile HabitTrackDao _habitTrackDao;

  private volatile ReminderDao _reminderDao;

  private volatile ChangeLogDao _changeLogDao;

  private volatile EfficiencyScoreDao _efficiencyScoreDao;

  private volatile TelemetryDao _telemetryDao;

  private volatile SyncStateDao _syncStateDao;

  private volatile InterceptionRuleDao _interceptionRuleDao;

  private volatile GoalChecklistDao _goalChecklistDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `goals` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `keywords` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_userId` ON `goals` (`userId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `daily_todo_items` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `text` TEXT NOT NULL, `dueDateTime` INTEGER, `isCompleted` INTEGER NOT NULL, `dayDate` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_todo_items_userId_dayDate` ON `daily_todo_items` (`userId`, `dayDate`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `goal_checklist_items` (`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, `userId` TEXT NOT NULL, `text` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_checklist_items_goalId` ON `goal_checklist_items` (`goalId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_checklist_items_userId` ON `goal_checklist_items` (`userId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `habit_tracks` (`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, `userId` TEXT NOT NULL, `startDate` TEXT NOT NULL, `currentDay` INTEGER NOT NULL, `isFinished` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_tracks_goalId` ON `habit_tracks` (`goalId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_tracks_userId` ON `habit_tracks` (`userId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `habit_checkpoints` (`id` TEXT NOT NULL, `habitTrackId` TEXT NOT NULL, `dayNumber` INTEGER NOT NULL, `description` TEXT NOT NULL, `status` TEXT NOT NULL, `completedAt` INTEGER, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_checkpoints_habitTrackId` ON `habit_checkpoints` (`habitTrackId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `reminders` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `scheduledTime` TEXT NOT NULL, `scheduledDate` TEXT, `recurrenceJson` TEXT, `conditionType` TEXT, `escalationIntervalMinutes` INTEGER NOT NULL, `maxEscalations` INTEGER NOT NULL, `currentEscalationCount` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_userId` ON `reminders` (`userId`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_entityId` ON `reminders` (`entityId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `change_log` (`id` TEXT NOT NULL, `entityId` TEXT NOT NULL, `entityType` TEXT NOT NULL, `field` TEXT NOT NULL, `value` TEXT, `timestamp` INTEGER NOT NULL, `deviceId` TEXT NOT NULL, `userId` TEXT NOT NULL, `isSynced` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_change_log_userId_timestamp` ON `change_log` (`userId`, `timestamp`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_change_log_entityId` ON `change_log` (`entityId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `efficiency_scores` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `scoreDate` TEXT NOT NULL, `productiveSeconds` INTEGER NOT NULL, `totalTrackedSeconds` INTEGER NOT NULL, `scorePercent` INTEGER, `computedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_efficiency_scores_userId_scoreDate` ON `efficiency_scores` (`userId`, `scoreDate`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `interception_rules` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `goalId` TEXT, `appPackageName` TEXT, `siteDomain` TEXT, `dailyAllowanceMinutes` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_interception_rules_userId` ON `interception_rules` (`userId`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `telemetry_events` (`id` TEXT NOT NULL, `appPackageName` TEXT NOT NULL, `foregroundSeconds` INTEGER NOT NULL, `date` TEXT NOT NULL, `recordedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `sync_state` (`key` TEXT NOT NULL, `lastSyncTimestamp` INTEGER NOT NULL, PRIMARY KEY(`key`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'ba1fb0e429e8b8048704ac7f601c8aeb')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `goals`");
        db.execSQL("DROP TABLE IF EXISTS `daily_todo_items`");
        db.execSQL("DROP TABLE IF EXISTS `goal_checklist_items`");
        db.execSQL("DROP TABLE IF EXISTS `habit_tracks`");
        db.execSQL("DROP TABLE IF EXISTS `habit_checkpoints`");
        db.execSQL("DROP TABLE IF EXISTS `reminders`");
        db.execSQL("DROP TABLE IF EXISTS `change_log`");
        db.execSQL("DROP TABLE IF EXISTS `efficiency_scores`");
        db.execSQL("DROP TABLE IF EXISTS `interception_rules`");
        db.execSQL("DROP TABLE IF EXISTS `telemetry_events`");
        db.execSQL("DROP TABLE IF EXISTS `sync_state`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsGoals = new HashMap<String, TableInfo.Column>(8);
        _columnsGoals.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoals.put("userId", new TableInfo.Column("userId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoals.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoals.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoals.put("keywords", new TableInfo.Column("keywords", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoals.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoals.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoals.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysGoals = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesGoals = new HashSet<TableInfo.Index>(1);
        _indicesGoals.add(new TableInfo.Index("index_goals_userId", false, Arrays.asList("userId"), Arrays.asList("ASC")));
        final TableInfo _infoGoals = new TableInfo("goals", _columnsGoals, _foreignKeysGoals, _indicesGoals);
        final TableInfo _existingGoals = TableInfo.read(db, "goals");
        if (!_infoGoals.equals(_existingGoals)) {
          return new RoomOpenHelper.ValidationResult(false, "goals(com.dopashift.data.local.entity.LocalGoal).\n"
                  + " Expected:\n" + _infoGoals + "\n"
                  + " Found:\n" + _existingGoals);
        }
        final HashMap<String, TableInfo.Column> _columnsDailyTodoItems = new HashMap<String, TableInfo.Column>(8);
        _columnsDailyTodoItems.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDailyTodoItems.put("userId", new TableInfo.Column("userId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDailyTodoItems.put("text", new TableInfo.Column("text", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDailyTodoItems.put("dueDateTime", new TableInfo.Column("dueDateTime", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDailyTodoItems.put("isCompleted", new TableInfo.Column("isCompleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDailyTodoItems.put("dayDate", new TableInfo.Column("dayDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDailyTodoItems.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDailyTodoItems.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDailyTodoItems = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDailyTodoItems = new HashSet<TableInfo.Index>(1);
        _indicesDailyTodoItems.add(new TableInfo.Index("index_daily_todo_items_userId_dayDate", false, Arrays.asList("userId", "dayDate"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoDailyTodoItems = new TableInfo("daily_todo_items", _columnsDailyTodoItems, _foreignKeysDailyTodoItems, _indicesDailyTodoItems);
        final TableInfo _existingDailyTodoItems = TableInfo.read(db, "daily_todo_items");
        if (!_infoDailyTodoItems.equals(_existingDailyTodoItems)) {
          return new RoomOpenHelper.ValidationResult(false, "daily_todo_items(com.dopashift.data.local.entity.LocalDailyTodo).\n"
                  + " Expected:\n" + _infoDailyTodoItems + "\n"
                  + " Found:\n" + _existingDailyTodoItems);
        }
        final HashMap<String, TableInfo.Column> _columnsGoalChecklistItems = new HashMap<String, TableInfo.Column>(7);
        _columnsGoalChecklistItems.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoalChecklistItems.put("goalId", new TableInfo.Column("goalId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoalChecklistItems.put("userId", new TableInfo.Column("userId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoalChecklistItems.put("text", new TableInfo.Column("text", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoalChecklistItems.put("isCompleted", new TableInfo.Column("isCompleted", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoalChecklistItems.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoalChecklistItems.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysGoalChecklistItems = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesGoalChecklistItems = new HashSet<TableInfo.Index>(2);
        _indicesGoalChecklistItems.add(new TableInfo.Index("index_goal_checklist_items_goalId", false, Arrays.asList("goalId"), Arrays.asList("ASC")));
        _indicesGoalChecklistItems.add(new TableInfo.Index("index_goal_checklist_items_userId", false, Arrays.asList("userId"), Arrays.asList("ASC")));
        final TableInfo _infoGoalChecklistItems = new TableInfo("goal_checklist_items", _columnsGoalChecklistItems, _foreignKeysGoalChecklistItems, _indicesGoalChecklistItems);
        final TableInfo _existingGoalChecklistItems = TableInfo.read(db, "goal_checklist_items");
        if (!_infoGoalChecklistItems.equals(_existingGoalChecklistItems)) {
          return new RoomOpenHelper.ValidationResult(false, "goal_checklist_items(com.dopashift.data.local.entity.LocalGoalChecklist).\n"
                  + " Expected:\n" + _infoGoalChecklistItems + "\n"
                  + " Found:\n" + _existingGoalChecklistItems);
        }
        final HashMap<String, TableInfo.Column> _columnsHabitTracks = new HashMap<String, TableInfo.Column>(8);
        _columnsHabitTracks.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHabitTracks.put("goalId", new TableInfo.Column("goalId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHabitTracks.put("userId", new TableInfo.Column("userId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHabitTracks.put("startDate", new TableInfo.Column("startDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHabitTracks.put("currentDay", new TableInfo.Column("currentDay", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHabitTracks.put("isFinished", new TableInfo.Column("isFinished", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHabitTracks.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHabitTracks.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysHabitTracks = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesHabitTracks = new HashSet<TableInfo.Index>(2);
        _indicesHabitTracks.add(new TableInfo.Index("index_habit_tracks_goalId", false, Arrays.asList("goalId"), Arrays.asList("ASC")));
        _indicesHabitTracks.add(new TableInfo.Index("index_habit_tracks_userId", false, Arrays.asList("userId"), Arrays.asList("ASC")));
        final TableInfo _infoHabitTracks = new TableInfo("habit_tracks", _columnsHabitTracks, _foreignKeysHabitTracks, _indicesHabitTracks);
        final TableInfo _existingHabitTracks = TableInfo.read(db, "habit_tracks");
        if (!_infoHabitTracks.equals(_existingHabitTracks)) {
          return new RoomOpenHelper.ValidationResult(false, "habit_tracks(com.dopashift.data.local.entity.LocalHabitTrack).\n"
                  + " Expected:\n" + _infoHabitTracks + "\n"
                  + " Found:\n" + _existingHabitTracks);
        }
        final HashMap<String, TableInfo.Column> _columnsHabitCheckpoints = new HashMap<String, TableInfo.Column>(6);
        _columnsHabitCheckpoints.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHabitCheckpoints.put("habitTrackId", new TableInfo.Column("habitTrackId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHabitCheckpoints.put("dayNumber", new TableInfo.Column("dayNumber", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHabitCheckpoints.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHabitCheckpoints.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHabitCheckpoints.put("completedAt", new TableInfo.Column("completedAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysHabitCheckpoints = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesHabitCheckpoints = new HashSet<TableInfo.Index>(1);
        _indicesHabitCheckpoints.add(new TableInfo.Index("index_habit_checkpoints_habitTrackId", false, Arrays.asList("habitTrackId"), Arrays.asList("ASC")));
        final TableInfo _infoHabitCheckpoints = new TableInfo("habit_checkpoints", _columnsHabitCheckpoints, _foreignKeysHabitCheckpoints, _indicesHabitCheckpoints);
        final TableInfo _existingHabitCheckpoints = TableInfo.read(db, "habit_checkpoints");
        if (!_infoHabitCheckpoints.equals(_existingHabitCheckpoints)) {
          return new RoomOpenHelper.ValidationResult(false, "habit_checkpoints(com.dopashift.data.local.entity.LocalHabitCheckpoint).\n"
                  + " Expected:\n" + _infoHabitCheckpoints + "\n"
                  + " Found:\n" + _existingHabitCheckpoints);
        }
        final HashMap<String, TableInfo.Column> _columnsReminders = new HashMap<String, TableInfo.Column>(14);
        _columnsReminders.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("userId", new TableInfo.Column("userId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("entityType", new TableInfo.Column("entityType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("entityId", new TableInfo.Column("entityId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("scheduledTime", new TableInfo.Column("scheduledTime", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("scheduledDate", new TableInfo.Column("scheduledDate", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("recurrenceJson", new TableInfo.Column("recurrenceJson", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("conditionType", new TableInfo.Column("conditionType", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("escalationIntervalMinutes", new TableInfo.Column("escalationIntervalMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("maxEscalations", new TableInfo.Column("maxEscalations", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("currentEscalationCount", new TableInfo.Column("currentEscalationCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsReminders.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysReminders = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesReminders = new HashSet<TableInfo.Index>(2);
        _indicesReminders.add(new TableInfo.Index("index_reminders_userId", false, Arrays.asList("userId"), Arrays.asList("ASC")));
        _indicesReminders.add(new TableInfo.Index("index_reminders_entityId", false, Arrays.asList("entityId"), Arrays.asList("ASC")));
        final TableInfo _infoReminders = new TableInfo("reminders", _columnsReminders, _foreignKeysReminders, _indicesReminders);
        final TableInfo _existingReminders = TableInfo.read(db, "reminders");
        if (!_infoReminders.equals(_existingReminders)) {
          return new RoomOpenHelper.ValidationResult(false, "reminders(com.dopashift.data.local.entity.LocalReminder).\n"
                  + " Expected:\n" + _infoReminders + "\n"
                  + " Found:\n" + _existingReminders);
        }
        final HashMap<String, TableInfo.Column> _columnsChangeLog = new HashMap<String, TableInfo.Column>(9);
        _columnsChangeLog.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChangeLog.put("entityId", new TableInfo.Column("entityId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChangeLog.put("entityType", new TableInfo.Column("entityType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChangeLog.put("field", new TableInfo.Column("field", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChangeLog.put("value", new TableInfo.Column("value", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChangeLog.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChangeLog.put("deviceId", new TableInfo.Column("deviceId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChangeLog.put("userId", new TableInfo.Column("userId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsChangeLog.put("isSynced", new TableInfo.Column("isSynced", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysChangeLog = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesChangeLog = new HashSet<TableInfo.Index>(2);
        _indicesChangeLog.add(new TableInfo.Index("index_change_log_userId_timestamp", false, Arrays.asList("userId", "timestamp"), Arrays.asList("ASC", "ASC")));
        _indicesChangeLog.add(new TableInfo.Index("index_change_log_entityId", false, Arrays.asList("entityId"), Arrays.asList("ASC")));
        final TableInfo _infoChangeLog = new TableInfo("change_log", _columnsChangeLog, _foreignKeysChangeLog, _indicesChangeLog);
        final TableInfo _existingChangeLog = TableInfo.read(db, "change_log");
        if (!_infoChangeLog.equals(_existingChangeLog)) {
          return new RoomOpenHelper.ValidationResult(false, "change_log(com.dopashift.data.local.entity.LocalChangeLogEntry).\n"
                  + " Expected:\n" + _infoChangeLog + "\n"
                  + " Found:\n" + _existingChangeLog);
        }
        final HashMap<String, TableInfo.Column> _columnsEfficiencyScores = new HashMap<String, TableInfo.Column>(7);
        _columnsEfficiencyScores.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEfficiencyScores.put("userId", new TableInfo.Column("userId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEfficiencyScores.put("scoreDate", new TableInfo.Column("scoreDate", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEfficiencyScores.put("productiveSeconds", new TableInfo.Column("productiveSeconds", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEfficiencyScores.put("totalTrackedSeconds", new TableInfo.Column("totalTrackedSeconds", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEfficiencyScores.put("scorePercent", new TableInfo.Column("scorePercent", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsEfficiencyScores.put("computedAt", new TableInfo.Column("computedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysEfficiencyScores = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesEfficiencyScores = new HashSet<TableInfo.Index>(1);
        _indicesEfficiencyScores.add(new TableInfo.Index("index_efficiency_scores_userId_scoreDate", false, Arrays.asList("userId", "scoreDate"), Arrays.asList("ASC", "ASC")));
        final TableInfo _infoEfficiencyScores = new TableInfo("efficiency_scores", _columnsEfficiencyScores, _foreignKeysEfficiencyScores, _indicesEfficiencyScores);
        final TableInfo _existingEfficiencyScores = TableInfo.read(db, "efficiency_scores");
        if (!_infoEfficiencyScores.equals(_existingEfficiencyScores)) {
          return new RoomOpenHelper.ValidationResult(false, "efficiency_scores(com.dopashift.data.local.entity.LocalEfficiencyScore).\n"
                  + " Expected:\n" + _infoEfficiencyScores + "\n"
                  + " Found:\n" + _existingEfficiencyScores);
        }
        final HashMap<String, TableInfo.Column> _columnsInterceptionRules = new HashMap<String, TableInfo.Column>(8);
        _columnsInterceptionRules.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsInterceptionRules.put("userId", new TableInfo.Column("userId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsInterceptionRules.put("goalId", new TableInfo.Column("goalId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsInterceptionRules.put("appPackageName", new TableInfo.Column("appPackageName", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsInterceptionRules.put("siteDomain", new TableInfo.Column("siteDomain", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsInterceptionRules.put("dailyAllowanceMinutes", new TableInfo.Column("dailyAllowanceMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsInterceptionRules.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsInterceptionRules.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysInterceptionRules = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesInterceptionRules = new HashSet<TableInfo.Index>(1);
        _indicesInterceptionRules.add(new TableInfo.Index("index_interception_rules_userId", false, Arrays.asList("userId"), Arrays.asList("ASC")));
        final TableInfo _infoInterceptionRules = new TableInfo("interception_rules", _columnsInterceptionRules, _foreignKeysInterceptionRules, _indicesInterceptionRules);
        final TableInfo _existingInterceptionRules = TableInfo.read(db, "interception_rules");
        if (!_infoInterceptionRules.equals(_existingInterceptionRules)) {
          return new RoomOpenHelper.ValidationResult(false, "interception_rules(com.dopashift.data.local.entity.LocalInterceptionRule).\n"
                  + " Expected:\n" + _infoInterceptionRules + "\n"
                  + " Found:\n" + _existingInterceptionRules);
        }
        final HashMap<String, TableInfo.Column> _columnsTelemetryEvents = new HashMap<String, TableInfo.Column>(5);
        _columnsTelemetryEvents.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTelemetryEvents.put("appPackageName", new TableInfo.Column("appPackageName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTelemetryEvents.put("foregroundSeconds", new TableInfo.Column("foregroundSeconds", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTelemetryEvents.put("date", new TableInfo.Column("date", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTelemetryEvents.put("recordedAt", new TableInfo.Column("recordedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTelemetryEvents = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTelemetryEvents = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTelemetryEvents = new TableInfo("telemetry_events", _columnsTelemetryEvents, _foreignKeysTelemetryEvents, _indicesTelemetryEvents);
        final TableInfo _existingTelemetryEvents = TableInfo.read(db, "telemetry_events");
        if (!_infoTelemetryEvents.equals(_existingTelemetryEvents)) {
          return new RoomOpenHelper.ValidationResult(false, "telemetry_events(com.dopashift.data.local.entity.LocalTelemetryEvent).\n"
                  + " Expected:\n" + _infoTelemetryEvents + "\n"
                  + " Found:\n" + _existingTelemetryEvents);
        }
        final HashMap<String, TableInfo.Column> _columnsSyncState = new HashMap<String, TableInfo.Column>(2);
        _columnsSyncState.put("key", new TableInfo.Column("key", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSyncState.put("lastSyncTimestamp", new TableInfo.Column("lastSyncTimestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSyncState = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSyncState = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSyncState = new TableInfo("sync_state", _columnsSyncState, _foreignKeysSyncState, _indicesSyncState);
        final TableInfo _existingSyncState = TableInfo.read(db, "sync_state");
        if (!_infoSyncState.equals(_existingSyncState)) {
          return new RoomOpenHelper.ValidationResult(false, "sync_state(com.dopashift.data.local.entity.LocalSyncState).\n"
                  + " Expected:\n" + _infoSyncState + "\n"
                  + " Found:\n" + _existingSyncState);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "ba1fb0e429e8b8048704ac7f601c8aeb", "bba51ff64daf3b0355cefc38166aedad");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "goals","daily_todo_items","goal_checklist_items","habit_tracks","habit_checkpoints","reminders","change_log","efficiency_scores","interception_rules","telemetry_events","sync_state");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `goals`");
      _db.execSQL("DELETE FROM `daily_todo_items`");
      _db.execSQL("DELETE FROM `goal_checklist_items`");
      _db.execSQL("DELETE FROM `habit_tracks`");
      _db.execSQL("DELETE FROM `habit_checkpoints`");
      _db.execSQL("DELETE FROM `reminders`");
      _db.execSQL("DELETE FROM `change_log`");
      _db.execSQL("DELETE FROM `efficiency_scores`");
      _db.execSQL("DELETE FROM `interception_rules`");
      _db.execSQL("DELETE FROM `telemetry_events`");
      _db.execSQL("DELETE FROM `sync_state`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(GoalDao.class, GoalDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(DailyTodoDao.class, DailyTodoDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(HabitTrackDao.class, HabitTrackDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ReminderDao.class, ReminderDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ChangeLogDao.class, ChangeLogDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(EfficiencyScoreDao.class, EfficiencyScoreDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TelemetryDao.class, TelemetryDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(SyncStateDao.class, SyncStateDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(InterceptionRuleDao.class, InterceptionRuleDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(GoalChecklistDao.class, GoalChecklistDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public GoalDao goalDao() {
    if (_goalDao != null) {
      return _goalDao;
    } else {
      synchronized(this) {
        if(_goalDao == null) {
          _goalDao = new GoalDao_Impl(this);
        }
        return _goalDao;
      }
    }
  }

  @Override
  public DailyTodoDao dailyTodoDao() {
    if (_dailyTodoDao != null) {
      return _dailyTodoDao;
    } else {
      synchronized(this) {
        if(_dailyTodoDao == null) {
          _dailyTodoDao = new DailyTodoDao_Impl(this);
        }
        return _dailyTodoDao;
      }
    }
  }

  @Override
  public HabitTrackDao habitTrackDao() {
    if (_habitTrackDao != null) {
      return _habitTrackDao;
    } else {
      synchronized(this) {
        if(_habitTrackDao == null) {
          _habitTrackDao = new HabitTrackDao_Impl(this);
        }
        return _habitTrackDao;
      }
    }
  }

  @Override
  public ReminderDao reminderDao() {
    if (_reminderDao != null) {
      return _reminderDao;
    } else {
      synchronized(this) {
        if(_reminderDao == null) {
          _reminderDao = new ReminderDao_Impl(this);
        }
        return _reminderDao;
      }
    }
  }

  @Override
  public ChangeLogDao changeLogDao() {
    if (_changeLogDao != null) {
      return _changeLogDao;
    } else {
      synchronized(this) {
        if(_changeLogDao == null) {
          _changeLogDao = new ChangeLogDao_Impl(this);
        }
        return _changeLogDao;
      }
    }
  }

  @Override
  public EfficiencyScoreDao efficiencyScoreDao() {
    if (_efficiencyScoreDao != null) {
      return _efficiencyScoreDao;
    } else {
      synchronized(this) {
        if(_efficiencyScoreDao == null) {
          _efficiencyScoreDao = new EfficiencyScoreDao_Impl(this);
        }
        return _efficiencyScoreDao;
      }
    }
  }

  @Override
  public TelemetryDao telemetryDao() {
    if (_telemetryDao != null) {
      return _telemetryDao;
    } else {
      synchronized(this) {
        if(_telemetryDao == null) {
          _telemetryDao = new TelemetryDao_Impl(this);
        }
        return _telemetryDao;
      }
    }
  }

  @Override
  public SyncStateDao syncStateDao() {
    if (_syncStateDao != null) {
      return _syncStateDao;
    } else {
      synchronized(this) {
        if(_syncStateDao == null) {
          _syncStateDao = new SyncStateDao_Impl(this);
        }
        return _syncStateDao;
      }
    }
  }

  @Override
  public InterceptionRuleDao interceptionRuleDao() {
    if (_interceptionRuleDao != null) {
      return _interceptionRuleDao;
    } else {
      synchronized(this) {
        if(_interceptionRuleDao == null) {
          _interceptionRuleDao = new InterceptionRuleDao_Impl(this);
        }
        return _interceptionRuleDao;
      }
    }
  }

  @Override
  public GoalChecklistDao goalChecklistDao() {
    if (_goalChecklistDao != null) {
      return _goalChecklistDao;
    } else {
      synchronized(this) {
        if(_goalChecklistDao == null) {
          _goalChecklistDao = new GoalChecklistDao_Impl(this);
        }
        return _goalChecklistDao;
      }
    }
  }
}
