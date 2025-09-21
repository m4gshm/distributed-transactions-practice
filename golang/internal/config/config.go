package config

import (
	"fmt"
	"os"
	"strconv"
)

type Config struct {
	Order   OrderConfig
	Payment ServiceConfig
	Reserve ServiceConfig
	TPC     ServiceConfig
}

type DatabaseConfig struct {
	Host     string
	Port     int
	User     string
	Password string
	DBName   string
	SSLMode  string
}

type OrderConfig struct {
	ServiceConfig
	PaymentServiceURL string
	ReserveServiceURL string
}

type ServiceConfig struct {
	GrpcPort int
	HttpPort int
	Database DatabaseConfig
}

func Load() *Config {
	payment := ServiceConfig{
		GrpcPort: getEnvInt("PAYMENTS_PORT", 9002),
		HttpPort: getEnvInt("PAYMENTS_PORT", 8002),
		Database: defDBConfig("payment"),
	}
	reserve := ServiceConfig{
		GrpcPort: getEnvInt("RESERVE_PORT", 9003),
		HttpPort: getEnvInt("RESERVE_PORT", 8003),
		Database: defDBConfig("reserve"),
	}
	return &Config{
		Order: OrderConfig{
			ServiceConfig: ServiceConfig{
				GrpcPort: getEnvInt("ORDERS_PORT", 9001),
				HttpPort: getEnvInt("ORDERS_PORT", 8001),
				Database: defDBConfig("orders"),
			},
			PaymentServiceURL: getEnv("ORDERS_PAYMENT_SERVICE_URL", fmt.Sprintf("localhost:%d", payment.GrpcPort)),
			ReserveServiceURL: getEnv("ORDERS_RESERVE_SERVICE_URL", fmt.Sprintf("localhost:%d", reserve.GrpcPort)),
		},
		Payment: payment,
		Reserve: reserve,
	}
}

func defDBConfig(dbNameDefault string) DatabaseConfig {
	return DatabaseConfig{
		Host:     getEnv("DB_HOST", "localhost"),
		Port:     getEnvInt("DB_PORT", 5000),
		User:     getEnv("DB_USER", "postgres"),
		Password: getEnv("DB_PASSWORD", "postgres"),
		DBName:   getEnv("DB_NAME", dbNameDefault),
		SSLMode:  getEnv("DB_SSL_MODE", "disable"),
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
