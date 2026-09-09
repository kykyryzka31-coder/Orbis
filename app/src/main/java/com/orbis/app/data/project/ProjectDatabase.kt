package com.orbis.app.data.project

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ProjectDatabase(context: Context) : SQLiteOpenHelper(
    context,
    "orbis.db",
    null,
    DATABASE_VERSION,
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE project_state (
                id INTEGER PRIMARY KEY CHECK(id = 1),
                name TEXT NOT NULL,
                provider_title TEXT NOT NULL,
                provider_style_uri TEXT NOT NULL,
                provider_kind TEXT NOT NULL,
                provider_max_native_zoom REAL,
                max_detail_enabled INTEGER NOT NULL,
                camera_lat REAL NOT NULL,
                camera_lon REAL NOT NULL,
                camera_zoom REAL NOT NULL,
                camera_bearing REAL NOT NULL,
                camera_tilt REAL NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE raster_layer (
                id TEXT PRIMARY KEY,
                project_id INTEGER NOT NULL DEFAULT 1,
                display_name TEXT NOT NULL,
                file_path TEXT NOT NULL,
                raster_format TEXT NOT NULL DEFAULT 'PMTILES',
                tile_size INTEGER,
                max_native_zoom REAL,
                opacity REAL NOT NULL,
                visible INTEGER NOT NULL,
                sort_order INTEGER NOT NULL,
                FOREIGN KEY(project_id) REFERENCES project_state(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX raster_layer_project_order ON raster_layer(project_id, sort_order)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE raster_layer ADD COLUMN raster_format TEXT NOT NULL DEFAULT 'PMTILES'")
            db.execSQL("ALTER TABLE raster_layer ADD COLUMN tile_size INTEGER")
            db.execSQL("ALTER TABLE raster_layer ADD COLUMN max_native_zoom REAL")
        }
    }

    companion object {
        private const val DATABASE_VERSION = 2
    }
}
