# Invoked by the app with positional arguments, inside one root shell.
set -eu
engine=$1; source_dir=$2; tcp=$3; udp=$4; mode=$5; owners=$6
shift 6
[ "$(id -u)" = 0 ] || { echo "Требуется root"; exit 1; }
umask 077
work=$(mktemp -d /data/local/tmp/zapret-mobile.XXXXXX)
chain=ZMOB_$$
created=""
pid=""
watcher=""
cleanup() {
    trap - EXIT HUP INT TERM
    set +e
    for tool in $created; do
        "$tool" -w -t mangle -D OUTPUT -j "$chain" 2>/dev/null
        "$tool" -w -t mangle -F "$chain"
        "$tool" -w -t mangle -X "$chain"
    done
    [ -z "$watcher" ] || kill "$watcher" 2>/dev/null
    [ -z "$pid" ] || kill "$pid" 2>/dev/null
    rm -rf "$work"
}
trap cleanup EXIT
trap 'exit 1' HUP INT TERM
cp "$source_dir"/*.bin "$source_dir"/*.txt "$work"/
chmod 755 "$work"
chmod 644 "$work"/*
cd "$work"
queue=27841
queue_table=/proc/net/netfilter/nfnetlink_queue
if grep -q "^[[:space:]]*$queue[[:space:]]" "$queue_table" 2>/dev/null; then
    echo "Очередь NFQUEUE уже занята"
    exit 1
fi
"$engine" --qnum=$queue --uid=1:3003 --dry-run "$@" || exit 1
"$engine" --qnum=$queue --uid=1:3003 "$@" &
pid=$!
# Confirm the queue belongs to a running worker before enabling interception.
tries=0
until grep -q "^[[:space:]]*$queue[[:space:]]" "$queue_table"; do
    kill -0 "$pid" && [ "$tries" -lt 40 ] || { echo "NFQUEUE недоступна"; exit 1; }
    tries=$((tries+1))
    sleep 0.1
done
sleep 0.1
kill -0 "$pid"
install_rules() {
    tool=$1
    "$tool" -w -t mangle -N "$chain"
    created="$tool $created"
    rule() { "$tool" -w -t mangle -A "$chain" "$@"; }
    rule -m mark --mark 0x40000000/0x40000000 -j RETURN
    rule -m owner --uid-owner 1 -j RETURN
    if [ "$mode" = blacklist ]; then
        for uid in $owners; do rule -m owner --uid-owner "$uid" -j RETURN; done
    fi
    for proto in tcp udp; do
        if [ "$proto" = tcp ]; then ports=$tcp; else ports=$udp; fi
        ports=$(echo "$ports" | tr '-' ':')
        if [ "$mode" = whitelist ]; then
            for uid in $owners; do
                rule -p "$proto" -m multiport --dports "$ports" -m owner --uid-owner "$uid" -j NFQUEUE --queue-num "$queue" --queue-bypass
            done
        else
            rule -p "$proto" -m multiport --dports "$ports" -j NFQUEUE --queue-num "$queue" --queue-bypass
        fi
    done
    "$tool" -w -t mangle -I OUTPUT 1 -j "$chain"
}
install_rules iptables
if [ -s /proc/net/if_inet6 ] || grep -q . /proc/net/if_inet6 2>/dev/null; then install_rules ip6tables; fi
echo ZMOB_READY
# EOF means the controlling app stopped, including process death.
exec 3<&0
cat <&3 >/dev/null &
watcher=$!
while kill -0 "$pid" 2>/dev/null && kill -0 "$watcher" 2>/dev/null; do sleep 1; done
