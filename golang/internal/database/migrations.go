package database

import (
	"context"
	"database/sql"
	// "fmt"
	// "io/ioutil"
	// "path/filepath"
	// "sort"
	// "strings"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

type DBTX interface {
	Exec(context.Context, string, ...any) (pgconn.CommandTag, error)
	Query(context.Context, string, ...any) (pgx.Rows, error)
	QueryRow(context.Context, string, ...any) pgx.Row
}

func RunMigrations(ctx context.Context, db DBTX, migrationPath string) error {
	return nil
	// // Create migrations table if it doesn't exist
	// _, err := db.Exec(ctx, `
	// 	CREATE TABLE IF NOT EXISTS migrations (
	// 		version VARCHAR(255) PRIMARY KEY,
	// 		applied_at TIMESTAMP DEFAULT NOW()
	// 	)
	// `)
	// if err != nil {
	// 	return fmt.Errorf("failed to create migrations table: %w", err)
	// }

	// // Get applied migrations
	// applied, err := getAppliedMigrations(db)
	// if err != nil {
	// 	return fmt.Errorf("failed to get applied migrations: %w", err)
	// }

	// // Read migration files
	// files, err := filepath.Glob(filepath.Join(migrationPath, "*.sql"))
	// if err != nil {
	// 	return fmt.Errorf("failed to read migration files: %w", err)
	// }

	// sort.Strings(files)

	// // Apply new migrations
	// for _, file := range files {
	// 	filename := filepath.Base(file)
	// 	version := strings.TrimSuffix(filename, ".sql")

	// 	if applied[version] {
	// 		continue // Skip already applied migrations
	// 	}

	// 	content, err := ioutil.ReadFile(file)
	// 	if err != nil {
	// 		return fmt.Errorf("failed to read migration file %s: %w", file, err)
	// 	}

	// 	// Execute migration
	// 	_, err = db.Exec(string(content))
	// 	if err != nil {
	// 		return fmt.Errorf("failed to execute migration %s: %w", version, err)
	// 	}

	// 	// Record migration as applied
	// 	_, err = db.Exec("INSERT INTO migrations (version) VALUES ($1)", version)
	// 	if err != nil {
	// 		return fmt.Errorf("failed to record migration %s: %w", version, err)
	// 	}

	// 	fmt.Printf("Applied migration: %s\n", version)
	// }

	// return nil
}

func getAppliedMigrations(db *sql.DB) (map[string]bool, error) {
	rows, err := db.Query("SELECT version FROM migrations")
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	applied := make(map[string]bool)
	for rows.Next() {
		var version string
		if err := rows.Scan(&version); err != nil {
			return nil, err
		}
		applied[version] = true
	}

	return applied, nil
}
