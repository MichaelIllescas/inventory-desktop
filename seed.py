import sqlite3
import random
from datetime import datetime, timedelta

DB_PATH = "data/inventory.db"

PRODUCTS_SQL = """
SELECT id, name, price FROM products WHERE code != '__VARIOS__'
"""

PAYMENT_METHODS = ["Efectivo", "Transferencia", "Débito", "Crédito"]

EXPENSES = [
    ("Alquiler", 180000),
    ("Luz", 32000),
    ("Internet", 12000),
    ("Teléfono", 8500),
    ("Seguro", 25000),
    ("Limpieza", 15000),
    ("Reparación heladera", 40000),
    ("Publicidad redes", 20000),
    ("Contador", 35000),
    ("Compra mercadería extra", 95000),
    ("Monotributo", 28000),
    ("Caja chica viáticos", 11000),
    ("Fletes", 18000),
]

EXPENSE_CATEGORIES = {
    "Alquiler": "Alquiler",
    "Luz": "Servicios",
    "Internet": "Servicios",
    "Teléfono": "Servicios",
    "Seguro": "Servicios",
    "Limpieza": "Mantenimiento",
    "Reparación heladera": "Mantenimiento",
    "Publicidad redes": "Otros",
    "Contador": "Personal",
    "Compra mercadería extra": "Proveedores",
    "Monotributo": "Impuestos",
    "Caja chica viáticos": "Otros",
    "Fletes": "Proveedores",
}

def random_datetime(year, month):
    import calendar
    _, days_in_month = calendar.monthrange(year, month)
    day = random.randint(1, days_in_month)
    hour = random.randint(8, 20)
    minute = random.randint(0, 59)
    second = random.randint(0, 59)
    return datetime(year, month, day, hour, minute, second)

conn = sqlite3.connect(DB_PATH)
cur = conn.cursor()

# Obtener productos existentes
cur.execute(PRODUCTS_SQL)
products = cur.fetchall()  # (id, name, price)

if not products:
    print("No hay productos cargados. Cargá al menos algunos productos primero.")
    conn.close()
    exit(1)

print(f"Encontré {len(products)} productos.")

sales_inserted = 0
items_inserted = 0
expenses_inserted = 0

year = 2026

for month in range(1, 5):  # enero a abril
    # Ventas: entre 80 y 140 por mes
    num_sales = random.randint(80, 140)
    for _ in range(num_sales):
        dt = random_datetime(year, month)
        date_str = dt.isoformat()
        payment = random.choice(PAYMENT_METHODS)

        # Entre 1 y 5 items por venta
        num_items = random.randint(1, 5)
        sale_products = random.choices(products, k=num_items)

        total = 0.0
        items = []
        for prod_id, prod_name, prod_price in sale_products:
            qty = round(random.uniform(1, 5), 0)
            # Precio con variación de hasta ±15%
            price = round(prod_price * random.uniform(0.85, 1.15), 2)
            subtotal = qty * price
            total += subtotal
            items.append((prod_id, qty, price))

        total = round(total, 2)
        cur.execute(
            "INSERT INTO sales(date, total, payment_method) VALUES (?, ?, ?)",
            (date_str, total, payment)
        )
        sale_id = cur.lastrowid
        sales_inserted += 1

        for prod_id, qty, price in items:
            cur.execute(
                "INSERT INTO sale_items(sale_id, product_id, quantity, price) VALUES (?, ?, ?, ?)",
                (sale_id, prod_id, qty, price)
            )
            items_inserted += 1

    # Gastos: entre 6 y 10 por mes
    num_expenses = random.randint(6, 10)
    chosen_expenses = random.sample(EXPENSES, k=min(num_expenses, len(EXPENSES)))
    for desc, base_amount in chosen_expenses:
        dt = random_datetime(year, month)
        date_str = dt.isoformat()
        amount = round(base_amount * random.uniform(0.9, 1.1), 2)
        category = EXPENSE_CATEGORIES[desc]
        cur.execute(
            "INSERT INTO expenses(date, description, category, amount) VALUES (?, ?, ?, ?)",
            (date_str, desc, category, amount)
        )
        expenses_inserted += 1

conn.commit()
conn.close()

print(f"Listo!")
print(f"  Ventas insertadas:  {sales_inserted}")
print(f"  Items insertados:   {items_inserted}")
print(f"  Gastos insertados:  {expenses_inserted}")
