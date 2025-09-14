package config

import (
	"os"
	"strconv"
)

type Config struct {
	Database DatabaseConfig
	Orders   ServiceConfig
	Payments ServiceConfig
	Reserve  ServiceConfig
	TPC      ServiceConfig
}

type DatabaseConfig struct {
	Host     string
	Port     int
	User     string
	Password string
	DBName   string
	SSLMode  string
}

type ServiceConfig struct {
	GrpcPort, HttpPort int
}

func Load(dbNameDefault string) *Config {
	return &Config{
		Database: DatabaseConfig{
			Host:     getEnv("DB_HOST", "localhost"),
			Port:     getEnvInt("DB_PORT", 5000),
			User:     getEnv("DB_USER", "postgres"),
			Password: getEnv("DB_PASSWORD", "postgres"),
			DBName:   getEnv("DB_NAME", dbNameDefault),
			SSLMode:  getEnv("DB_SSL_MODE", "disable"),
		},
		Orders: ServiceConfig{
			GrpcPort: getEnvInt("ORDERS_PORT", 9001),
			HttpPort: getEnvInt("ORDERS_PORT", 8001),
		},
		Payments: ServiceConfig{
			GrpcPort: getEnvInt("PAYMENTS_PORT", 9002),
			HttpPort: getEnvInt("PAYMENTS_PORT", 8002),
		},
		Reserve: ServiceConfig{
			GrpcPort: getEnvInt("RESERVE_PORT", 9003),
			HttpPort: getEnvInt("RESERVE_PORT", 8003),
		},
	}
}

func getEnv(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func getEnvInt(key string, defaultValue int) int {
	if value := os.Getenv(key); value != "" {
		if intValue, err := strconv.Atoi(value); err == nil {
			return intValue
		}
	}
	return defaultValue
}
