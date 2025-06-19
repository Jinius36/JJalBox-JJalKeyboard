from flask import Flask, request, jsonify
import psycopg2
import json

app = Flask(__name__)

# PostgreSQL 접속 정보 (예시)
DB_HOST = "jjalbox-database.c5yc2uk4knc2.ap-southeast-2.rds.amazonaws.com"
DB_NAME = "postgres"
DB_USER = "postgres"
DB_PASS = "YOUR_DB_PASSWORD"
DB_PORT = 5432

def get_db_connection():
    return psycopg2.connect(
        host=DB_HOST,
        dbname=DB_NAME,
        user=DB_USER,
        password=DB_PASS,
        port=DB_PORT
    )

@app.route("/images", methods=["POST"])
def add_image_metadata():
    data = request.json
    url = data.get("url")
    tags = data.get("tag", [])      # 여전히 list로 받되, jsonb로 저장
    text = data.get("text", "")

    if not url:
        return jsonify({"error": "url is required"}), 400

    try:
        conn = get_db_connection()
        cur = conn.cursor()
        cur.execute(
            "INSERT INTO jjal_metadata (url, tag, text) VALUES (%s, %s::jsonb, %s)",
            (url, json.dumps(tags), text)
        )
        conn.commit()
        cur.close()
        conn.close()
        return jsonify({"message": "Metadata inserted"}), 201
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
