# Lets tests import the tools as modules (tools/inventory/inventory.py → `import inventory`).
import os
import sys

HERE = os.path.dirname(__file__)
sys.path.insert(0, os.path.join(HERE, "inventory"))
sys.path.insert(0, os.path.join(HERE, "linernotes"))
sys.path.insert(0, os.path.join(HERE, "beets"))
