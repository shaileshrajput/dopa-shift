package com.dopashift.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.dopashift.data.local.entity.LocalHabitCheckpoint;
import com.dopashift.data.local.entity.LocalHabitTrack;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class HabitTrackDao_Impl implements HabitTrackDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<LocalHabitTrack> __insertionAdapterOfLocalHabitTrack;

  private final EntityInsertionAdapter<LocalHabitCheckpoint> __insertionAdapterOfLocalHabitCheckpoint;

  public HabitTrackDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfLocalHabitTrack = new EntityInsertionAdapter<LocalHabitTrack>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `habit_tracks` (`id`,`goalId`,`userId`,`startDate`,`currentDay`,`isFinished`,`createdAt`,`updatedAt`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LocalHabitTrack entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getGoalId());
        statement.bindString(3, entity.getUserId());
        statement.bindString(4, entity.getStartDate());
        statement.bindLong(5, entity.getCurrentDay());
        final int _tmp = entity.isFinished() ? 1 : 0;
        statement.bindLong(6, _tmp);
        statement.bindLong(7, entity.getCreatedAt());
        statement.bindLong(8, entity.getUpdatedAt());
      }
    };
    this.__insertionAdapterOfLocalHabitCheckpoint = new EntityInsertionAdapter<LocalHabitCheckpoint>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `habit_checkpoints` (`id`,`habitTrackId`,`dayNumber`,`description`,`status`,`completedAt`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final LocalHabitCheckpoint entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getHabitTrackId());
        statement.bindLong(3, entity.getDayNumber());
        statement.bindString(4, entity.getDescription());
        statement.bindString(5, entity.getStatus());
        if (entity.getCompletedAt() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getCompletedAt());
        }
      }
    };
  }

  @Override
  public Object upsert(final LocalHabitTrack track, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLocalHabitTrack.insert(track);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object upsertCheckpoint(final LocalHabitCheckpoint checkpoint,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfLocalHabitCheckpoint.insert(checkpoint);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object findById(final String id, final Continuation<? super LocalHabitTrack> $completion) {
    final String _sql = "SELECT * FROM habit_tracks WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LocalHabitTrack>() {
      @Override
      @Nullable
      public LocalHabitTrack call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfGoalId = CursorUtil.getColumnIndexOrThrow(_cursor, "goalId");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfCurrentDay = CursorUtil.getColumnIndexOrThrow(_cursor, "currentDay");
          final int _cursorIndexOfIsFinished = CursorUtil.getColumnIndexOrThrow(_cursor, "isFinished");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final LocalHabitTrack _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpGoalId;
            _tmpGoalId = _cursor.getString(_cursorIndexOfGoalId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpStartDate;
            _tmpStartDate = _cursor.getString(_cursorIndexOfStartDate);
            final int _tmpCurrentDay;
            _tmpCurrentDay = _cursor.getInt(_cursorIndexOfCurrentDay);
            final boolean _tmpIsFinished;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFinished);
            _tmpIsFinished = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _result = new LocalHabitTrack(_tmpId,_tmpGoalId,_tmpUserId,_tmpStartDate,_tmpCurrentDay,_tmpIsFinished,_tmpCreatedAt,_tmpUpdatedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findActiveByGoalId(final String goalId,
      final Continuation<? super List<LocalHabitTrack>> $completion) {
    final String _sql = "SELECT * FROM habit_tracks WHERE goalId = ? AND isFinished = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, goalId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalHabitTrack>>() {
      @Override
      @NonNull
      public List<LocalHabitTrack> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfGoalId = CursorUtil.getColumnIndexOrThrow(_cursor, "goalId");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfCurrentDay = CursorUtil.getColumnIndexOrThrow(_cursor, "currentDay");
          final int _cursorIndexOfIsFinished = CursorUtil.getColumnIndexOrThrow(_cursor, "isFinished");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<LocalHabitTrack> _result = new ArrayList<LocalHabitTrack>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalHabitTrack _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpGoalId;
            _tmpGoalId = _cursor.getString(_cursorIndexOfGoalId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpStartDate;
            _tmpStartDate = _cursor.getString(_cursorIndexOfStartDate);
            final int _tmpCurrentDay;
            _tmpCurrentDay = _cursor.getInt(_cursorIndexOfCurrentDay);
            final boolean _tmpIsFinished;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFinished);
            _tmpIsFinished = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new LocalHabitTrack(_tmpId,_tmpGoalId,_tmpUserId,_tmpStartDate,_tmpCurrentDay,_tmpIsFinished,_tmpCreatedAt,_tmpUpdatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findActiveByUserId(final String userId,
      final Continuation<? super List<LocalHabitTrack>> $completion) {
    final String _sql = "SELECT * FROM habit_tracks WHERE userId = ? AND isFinished = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalHabitTrack>>() {
      @Override
      @NonNull
      public List<LocalHabitTrack> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfGoalId = CursorUtil.getColumnIndexOrThrow(_cursor, "goalId");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfCurrentDay = CursorUtil.getColumnIndexOrThrow(_cursor, "currentDay");
          final int _cursorIndexOfIsFinished = CursorUtil.getColumnIndexOrThrow(_cursor, "isFinished");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<LocalHabitTrack> _result = new ArrayList<LocalHabitTrack>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalHabitTrack _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpGoalId;
            _tmpGoalId = _cursor.getString(_cursorIndexOfGoalId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpStartDate;
            _tmpStartDate = _cursor.getString(_cursorIndexOfStartDate);
            final int _tmpCurrentDay;
            _tmpCurrentDay = _cursor.getInt(_cursorIndexOfCurrentDay);
            final boolean _tmpIsFinished;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFinished);
            _tmpIsFinished = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new LocalHabitTrack(_tmpId,_tmpGoalId,_tmpUserId,_tmpStartDate,_tmpCurrentDay,_tmpIsFinished,_tmpCreatedAt,_tmpUpdatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<LocalHabitTrack>> observeActiveByUserId(final String userId) {
    final String _sql = "SELECT * FROM habit_tracks WHERE userId = ? AND isFinished = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, userId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"habit_tracks"}, new Callable<List<LocalHabitTrack>>() {
      @Override
      @NonNull
      public List<LocalHabitTrack> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfGoalId = CursorUtil.getColumnIndexOrThrow(_cursor, "goalId");
          final int _cursorIndexOfUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "userId");
          final int _cursorIndexOfStartDate = CursorUtil.getColumnIndexOrThrow(_cursor, "startDate");
          final int _cursorIndexOfCurrentDay = CursorUtil.getColumnIndexOrThrow(_cursor, "currentDay");
          final int _cursorIndexOfIsFinished = CursorUtil.getColumnIndexOrThrow(_cursor, "isFinished");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final List<LocalHabitTrack> _result = new ArrayList<LocalHabitTrack>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalHabitTrack _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpGoalId;
            _tmpGoalId = _cursor.getString(_cursorIndexOfGoalId);
            final String _tmpUserId;
            _tmpUserId = _cursor.getString(_cursorIndexOfUserId);
            final String _tmpStartDate;
            _tmpStartDate = _cursor.getString(_cursorIndexOfStartDate);
            final int _tmpCurrentDay;
            _tmpCurrentDay = _cursor.getInt(_cursorIndexOfCurrentDay);
            final boolean _tmpIsFinished;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsFinished);
            _tmpIsFinished = _tmp != 0;
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            _item = new LocalHabitTrack(_tmpId,_tmpGoalId,_tmpUserId,_tmpStartDate,_tmpCurrentDay,_tmpIsFinished,_tmpCreatedAt,_tmpUpdatedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getCheckpoints(final String trackId,
      final Continuation<? super List<LocalHabitCheckpoint>> $completion) {
    final String _sql = "SELECT * FROM habit_checkpoints WHERE habitTrackId = ? ORDER BY dayNumber";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, trackId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<LocalHabitCheckpoint>>() {
      @Override
      @NonNull
      public List<LocalHabitCheckpoint> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfHabitTrackId = CursorUtil.getColumnIndexOrThrow(_cursor, "habitTrackId");
          final int _cursorIndexOfDayNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "dayNumber");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final List<LocalHabitCheckpoint> _result = new ArrayList<LocalHabitCheckpoint>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final LocalHabitCheckpoint _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpHabitTrackId;
            _tmpHabitTrackId = _cursor.getString(_cursorIndexOfHabitTrackId);
            final int _tmpDayNumber;
            _tmpDayNumber = _cursor.getInt(_cursorIndexOfDayNumber);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final Long _tmpCompletedAt;
            if (_cursor.isNull(_cursorIndexOfCompletedAt)) {
              _tmpCompletedAt = null;
            } else {
              _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            }
            _item = new LocalHabitCheckpoint(_tmpId,_tmpHabitTrackId,_tmpDayNumber,_tmpDescription,_tmpStatus,_tmpCompletedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findCheckpointById(final String checkpointId,
      final Continuation<? super LocalHabitCheckpoint> $completion) {
    final String _sql = "SELECT * FROM habit_checkpoints WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, checkpointId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<LocalHabitCheckpoint>() {
      @Override
      @Nullable
      public LocalHabitCheckpoint call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfHabitTrackId = CursorUtil.getColumnIndexOrThrow(_cursor, "habitTrackId");
          final int _cursorIndexOfDayNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "dayNumber");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfCompletedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "completedAt");
          final LocalHabitCheckpoint _result;
          if (_cursor.moveToFirst()) {
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpHabitTrackId;
            _tmpHabitTrackId = _cursor.getString(_cursorIndexOfHabitTrackId);
            final int _tmpDayNumber;
            _tmpDayNumber = _cursor.getInt(_cursorIndexOfDayNumber);
            final String _tmpDescription;
            _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final Long _tmpCompletedAt;
            if (_cursor.isNull(_cursorIndexOfCompletedAt)) {
              _tmpCompletedAt = null;
            } else {
              _tmpCompletedAt = _cursor.getLong(_cursorIndexOfCompletedAt);
            }
            _result = new LocalHabitCheckpoint(_tmpId,_tmpHabitTrackId,_tmpDayNumber,_tmpDescription,_tmpStatus,_tmpCompletedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
