##  Steps to run cluster

0) install pip, python libs, haproxy, honcho
```bash
apt-get install pip haproxy
pip install honcho
``` 
1) clone patroni repo, install dependencies
```bash
git clone git@github.com:zalando/patroni.git
cd patroni
sudo pip install -r requirements.txt
```
2) Create link
```bash
sudo ln -s $(pwd)/patroni.py /usr/bin/patroni
```
3) go to this directory
4) run consul
```bash
consul agent -dev -client 0.0.0.0 -data-dir /tmp/data/consul
```
5) run haproxy
```bash
haproxy -f haproxy.cfg
```
6) run postgres nodes
```bash
patroni postgres0.yml
patroni postgres1.yml
patroni postgres2.yml
```
6-alternative) run postgres nodes, using honcho. It reads Procfile
```bash
honcho start
```
#### Notes
in application use haproxy port: 5000
original postgres ports are configured in *.yml files (postgresql.listen): 5432, 5433, 5434

## *.yml configuration

#### Major properties for sync replication:
maximum_lag_on_failover: 0
synchronous_mode: true
synchronous_commit: "on"
synchronous_standby_names: "*"