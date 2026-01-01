from setuptools import setup, find_packages

setup(
    name="mccli",
    version="1.0.0",
    packages=find_packages(),
    entry_points={
        "console_scripts": [
            "mccli=mccli.__main__:main",
        ],
    },
    python_requires=">=3.9",
)
