import grpc from 'k6/net/grpc';
import { check } from 'k6';

const ORDER_ADDRESS = __ENV.ORDER_ADDRESS || 'localhost:9080';

const ordersClient = new grpc.Client();
ordersClient.load([
    '../proto/order/v1/api',
    '../proto',
], 'order_service.proto');

export function setup() {
}

export function teardown(data) {
}

export default function () {
    ordersClient.connect(ORDER_ADDRESS, { plaintext: true });

    const data = {
        "body": {
            "items": [
                {
                    "id": "f7c36185-f570-4e6b-b1b2-f3f0f9c46135",
                    "amount": 1
                },
                {
                    "id": "374fabf8-dd31-4912-93b1-57d177b9f6c6",
                    "amount": 3
                }
            ],
            "customerId": "f54e7dc2-f8aa-45bc-b632-ea0c15eaa5e2",
            "delivery": {
                "dateTime": "2025-07-26T17:03:13.475Z",
                "address": "Lenina st., 1",
                "type": "PICKUP"
            }
        },
        "twoPhaseCommit": false
    }

    const response = ordersClient.invoke('orders.v1.OrderService/Create', data);

    check(response, {
        'status is OK': (r) => {
            return r.status === grpc.StatusOK
        },
        'Returned ID': (r) => {
            const out = r.message

            const id = out.id
            const vaild = id != undefined && id !== null && id.trim().length > 0;
            if (!vaild) {
                console.log("invalid response id:" + id)
            }
            return vaild
        },
    });

    ordersClient.close();
}
