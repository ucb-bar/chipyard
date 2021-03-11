
source .github/scripts/defaults.sh

echo "Env ============================="
env | sort # | grep -v '^GITHUB_'
#echo "mapping ============================="
#echo ${mapping[@]}
echo "CIRCLE ============================="
env | sort | grep -i circle
echo "done ============================="
